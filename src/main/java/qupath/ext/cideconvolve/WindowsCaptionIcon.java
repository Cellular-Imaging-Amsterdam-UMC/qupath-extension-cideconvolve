package qupath.ext.cideconvolve;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import javafx.scene.image.Image;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class WindowsCaptionIcon {

    private static final int WM_SETICON = 0x0080;
    private static final int DIB_HEADER_SIZE = 40;
    // Windows retains these two handles, which are shared by all CI Deconvolve windows.
    private static Pointer smallIcon;
    private static Pointer largeIcon;

    private WindowsCaptionIcon() {
    }

    static void apply(String title, Image small, Image large) {
        try {
            var user = User32.INSTANCE;
            user.EnumThreadWindows(Kernel32.INSTANCE.GetCurrentThreadId(), (window, data) -> {
                var caption = new char[128];
                if (user.GetWindowTextW(window, caption, caption.length) == 0 || !title.equals(Native.toString(caption)))
                    return true;
                if (smallIcon == null)
                    smallIcon = iconHandle(user, small);
                if (largeIcon == null)
                    largeIcon = iconHandle(user, large);
                if (smallIcon != null)
                    user.SendMessageW(window, WM_SETICON, null, smallIcon);
                if (largeIcon != null)
                    user.SendMessageW(window, WM_SETICON, Pointer.createConstant(1), largeIcon);
                return false;
            }, null);
        } catch (RuntimeException | LinkageError error) {
            LoggerFactory.getLogger(WindowsCaptionIcon.class).debug("Could not set the Windows caption icon", error);
        }
    }

    private static Pointer iconHandle(User32 user, Image image) {
        int size = (int) image.getWidth();
        int stride = ((size + 31) / 32) * Integer.BYTES;
        int bitmapSize = size * size * Integer.BYTES;
        var icon = ByteBuffer.allocate(DIB_HEADER_SIZE + bitmapSize + stride * size).order(ByteOrder.LITTLE_ENDIAN);
        icon.putInt(DIB_HEADER_SIZE).putInt(size).putInt(size * 2).putShort((short) 1).putShort((short) 32);
        icon.position(DIB_HEADER_SIZE);

        var mask = new byte[stride * size];
        var pixels = image.getPixelReader();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int color = pixels.getArgb(x, size - y - 1);
                icon.putInt(color);
                if (color >>> 24 == 0)
                    mask[y * stride + x / 8] |= (byte) (0x80 >>> (x % 8));
            }
        }
        icon.put(mask);
        return user.CreateIconFromResourceEx(icon.array(), icon.capacity(), true, 0x00030000, size, size, 0);
    }

    interface WindowCallback extends StdCallLibrary.StdCallCallback {
        boolean invoke(Pointer window, Pointer data);
    }

    interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);

        boolean EnumThreadWindows(int threadId, WindowCallback callback, Pointer data);
        int GetWindowTextW(Pointer window, char[] title, int length);
        Pointer SendMessageW(Pointer window, int message, Pointer wParam, Pointer lParam);
        Pointer CreateIconFromResourceEx(byte[] bytes, int length, boolean icon, int version, int width, int height, int flags);
    }

    interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        int GetCurrentThreadId();
    }
}
