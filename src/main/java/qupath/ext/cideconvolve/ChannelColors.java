package qupath.ext.cideconvolve;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import loci.common.RandomAccessInputStream;
import loci.formats.tiff.TiffParser;
import loci.formats.tiff.TiffSaver;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Restore the channel colors QuPath displayed for the source in both OME metadata formats. */
final class ChannelColors {
    private ChannelColors() {}

    static void write(Path output, List<ImageChannel> channels) throws Exception {
        if (channels.isEmpty())
            return;
        if (Files.isDirectory(output)) {
            var xml = output.resolve("OME").resolve("METADATA.ome.xml");
            if (Files.isRegularFile(xml)) {
                String original = Files.readString(xml);
                String updated = patchXml(original, channels);
                if (!updated.equals(original))
                    replaceText(xml, updated);
            }
            var attrs = output.resolve(".zattrs");
            if (Files.isRegularFile(attrs))
                patchZarrRendering(attrs, channels);
        } else {
            try (var input = new RandomAccessInputStream(output.toString())) {
                String original = new TiffParser(input).getComment();
                if (original == null || !original.contains("<OME"))
                    return;
                String updated = patchXml(original, channels);
                if (!updated.equals(original)) {
                    try (var saver = new TiffSaver(output.toString())) {
                        saver.overwriteComment(input, updated);
                    }
                }
            }
        }
    }

    private static String patchXml(String xml, List<ImageChannel> channels) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        var images = document.getElementsByTagNameNS("*", "Image");
        if (images.getLength() != 1)
            return xml;
        var elements = ((Element)images.item(0)).getElementsByTagNameNS("*", "Channel");
        if (elements.getLength() != channels.size())
            return xml;
        boolean changed = false;
        for (int c = 0; c < channels.size(); c++) {
            Integer color = channels.get(c).getColor();
            if (color == null)
                continue;
            var channel = (Element)elements.item(c);
            String rgba = Integer.toString((color & 0xffffff) << 8 | 0xff);
            if (!rgba.equals(channel.getAttribute("Color"))) {
                channel.setAttribute("Color", rgba);
                changed = true;
            }
        }
        if (!changed)
            return xml;
        var transformerFactory = TransformerFactory.newDefaultInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var result = new StringWriter();
        transformerFactory.newTransformer().transform(new DOMSource(document), new StreamResult(result));
        return result.toString();
    }

    private static void patchZarrRendering(Path attrs, List<ImageChannel> channels) throws IOException {
        var root = JsonParser.parseString(Files.readString(attrs)).getAsJsonObject();
        if (!(root.get("omero") instanceof JsonObject omero))
            return;
        var displayed = omero.getAsJsonArray("channels");
        if (displayed == null || displayed.size() != channels.size())
            return;
        for (int c = 0; c < channels.size(); c++) {
            Integer color = channels.get(c).getColor();
            if (color != null)
                displayed.get(c).getAsJsonObject().addProperty("color", String.format(Locale.ROOT, "%06X", color & 0xffffff));
        }
        replaceText(attrs, new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    private static void replaceText(Path target, String text) throws IOException {
        var temp = Files.createTempFile(target.getParent(), "cideconvolve-colors-", ".tmp");
        try {
            Files.writeString(temp, text);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static void apply(ImageServer<?> server, List<ImageChannel> source) {
        if (server.isRGB() || server.nChannels() != source.size())
            return;
        var channels = new ArrayList<ImageChannel>();
        for (int c = 0; c < source.size(); c++) {
            var output = server.getChannel(c);
            var color = source.get(c).getColor();
            channels.add(color == null ? output : ImageChannel.getInstance(output.getName(), color));
        }
        if (!channels.equals(server.getMetadata().getChannels()))
            server.setMetadata(new ImageServerMetadata.Builder(server.getMetadata()).channels(channels).build());
    }
}
