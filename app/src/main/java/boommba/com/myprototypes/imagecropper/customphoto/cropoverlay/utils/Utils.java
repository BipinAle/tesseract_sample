package boommba.com.myprototypes.imagecropper.customphoto.cropoverlay.utils;

import android.net.Uri;

import java.io.File;

/**
 * @author albin
 * @date 24/6/15
 */
public class Utils {

    public static Uri getImageUri(String path) {
        return Uri.fromFile(new File(path));
    }
}
