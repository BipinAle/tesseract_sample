package boommba.com.myprototypes.imagecropper;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import boommba.com.myprototypes.CircleOverLayView;
import boommba.com.myprototypes.R;
import boommba.com.myprototypes.imagecropper.customphoto.cropoverlay.CropOverlayView;
import boommba.com.myprototypes.imagecropper.customphoto.cropoverlay.edge.Edge;
import boommba.com.myprototypes.imagecropper.customphoto.cropoverlay.utils.ConstantsImageCrop;
import boommba.com.myprototypes.imagecropper.customphoto.cropoverlay.utils.ImageViewUtil;
import boommba.com.myprototypes.imagecropper.customphoto.cropoverlay.utils.InternalStorageContentProvider;
import boommba.com.myprototypes.imagecropper.customphoto.cropoverlay.utils.Utils;
import boommba.com.myprototypes.imagecropper.customphoto.photoview.PhotoView;
import boommba.com.myprototypes.imagecropper.customphoto.photoview.PhotoViewAttacher;


interface ICropImageLikeInstagram {
    void initViews();

    void findViews();

    void makeLayoutSquare();

    void initUserImage();

    void hideCropping();

    void showCropping();

    void onGetImages(String action);

    void createTempFile();

    void takePic();

    void pickImage();

    void initClickListner();
}


@SuppressWarnings("ALL")
public class CropImageLikeInstagram extends AppCompatActivity implements ICropImageLikeInstagram, View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {
    public static final String TEMP_PHOTO_FILE_NAME = "temp_photo.jpg";

    private static final int REQUEST_CODE_PICK_GALLERY = 0x1;
    private static final int REQUEST_CODE_TAKE_PICTURE = 0x2;
    private static final int REQUEST_CAMERA = 0;
    private static final int REQUEST_STORAGE = 1;
    private final int IMAGE_MAX_SIZE = 1024;
    private final Bitmap.CompressFormat mOutputFormat = Bitmap.CompressFormat.JPEG;
    private float minScale = 1f;
    private RelativeLayout relativeImage;
    private Button btnTakePicture, btnChooseGallery, cropDone;
    private PhotoView photoView;
    private CropOverlayView cropOverlayView;
    private ImageView imgImage;
    private File mFileTemp;
    private String currentDateandTime = "";
    private String mImagePath = null;
    private Uri mSaveUri = null;
    private Uri mImageUri = null;
    private ContentResolver mContentResolver;


    //for camera permission
    private boolean isUpdated = false;
    private boolean isDeleted = false;


    public TextView scanResultView;

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[512];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    private TextView resultView;

    public static int getCameraPhotoOrientation(@NonNull Context context, Uri imageUri) {
        int rotate = 0;
        try {
            context.getContentResolver().notifyChange(imageUri, null);
            ExifInterface exif = new ExifInterface(
                    imageUri.getPath());
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rotate;
    }

    public static boolean createDirIfNotExists() {
        boolean ret = true;
        File file = new File(Environment.getExternalStorageDirectory()+ "EasyRecharge/tessdata");
        if (!file.exists()) {
            if (!file.mkdirs()) {
                ret = false;
            }
        }
        return ret;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cropper_main);

        createDirIfNotExists();
        findViews();
        initViews();
        scanResultView = (TextView) findViewById(R.id.tv_scan_result);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestExternlStoragePermission();
        }

    }

    private void requestCameraPermission() {

        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {

            ActivityCompat.requestPermissions(CropImageLikeInstagram.this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA);
        } else {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA);
        }
    }

    private void requestExternlStoragePermission() {

        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

            ActivityCompat.requestPermissions(CropImageLikeInstagram.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE);
        } else {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE);
        }
    }

    private void showCameraPreview() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            Uri mImageCaptureUri = null;
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                mImageCaptureUri = Uri.fromFile(mFileTemp);
            } else {
                mImageCaptureUri = InternalStorageContentProvider.CONTENT_URI;
            }
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageCaptureUri);
            takePictureIntent.putExtra("return-data", true);
            startActivityForResult(takePictureIntent, REQUEST_CODE_TAKE_PICTURE);
        } catch (ActivityNotFoundException e) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePic();
            } else {
                Toast.makeText(this, "CAMERA permission was NOT granted.", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, "Storage permission was NOT granted.", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void initViews() {
        mContentResolver = getContentResolver();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        currentDateandTime = sdf.format(new Date());
        makeLayoutSquare();
        initUserImage();
        initClickListner();
        photoView.addListener(new PhotoViewAttacher.IGetImageBounds() {
            @Override
            public Rect getImageBounds() {
                return new Rect((int) Edge.LEFT.getCoordinate(), (int) Edge.TOP.getCoordinate(), (int) Edge.RIGHT.getCoordinate(), (int) Edge.BOTTOM.getCoordinate());
            }
        });
    }

    @Override
    public void findViews() {
        btnTakePicture = (Button) findViewById(R.id.btnTakePicture);
        btnChooseGallery = (Button) findViewById(R.id.btnChooseGallery);
        relativeImage = (RelativeLayout) findViewById(R.id.relativeImage);
        photoView = (PhotoView) findViewById(R.id.photoView);
        cropOverlayView = (CropOverlayView) findViewById(R.id.cropOverlayView);
        imgImage = (ImageView) findViewById(R.id.imgImage);
        cropDone = (Button) findViewById(R.id.doneCrop);


    }

    @Override
    public void makeLayoutSquare() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, width);
        relativeImage.setLayoutParams(params);
    }

    @Override
    public void initUserImage() {

    }

    @Override
    public void hideCropping() {
        imgImage.setVisibility(View.VISIBLE);
        photoView.setVisibility(View.GONE);
        cropOverlayView.setVisibility(View.GONE);
        findViewById(R.id.test).setVisibility(View.GONE);
    }

    @Override
    public void showCropping() {
        imgImage.setVisibility(View.GONE);
        photoView.setVisibility(View.VISIBLE);
        cropOverlayView.setVisibility(View.GONE);
        findViewById(R.id.test).setVisibility(View.VISIBLE);
    }

    @Override
    public void initClickListner() {
        btnTakePicture.setOnClickListener(this);
        btnChooseGallery.setOnClickListener(this);
        cropDone.setOnClickListener(this);

    }

    @Override
    public void onGetImages(String action) {
        createTempFile();
        if (null != action) {
            switch (action) {
                case ConstantsImageCrop.IntentExtras.ACTION_CAMERA:
                    getIntent().removeExtra("ACTION");
                    takePic();
                    return;
                case ConstantsImageCrop.IntentExtras.ACTION_GALLERY:
                    getIntent().removeExtra("ACTION");
                    pickImage();
                    return;
            }
        }

    }

    @Override
    public void createTempFile() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {

            mFileTemp = new File(Environment.getExternalStorageDirectory() + "/EasyRecharge", currentDateandTime + TEMP_PHOTO_FILE_NAME);
        } else {

            mFileTemp = new File(getFilesDir() + "/EasyRecharge", currentDateandTime + TEMP_PHOTO_FILE_NAME);
        }
    }

    @Override
    public void takePic() {


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();

        } else {
            showCameraPreview();
        }


    }
/*
    private void init() {
        showCropping();
        Bitmap b = getBitmap(mImageUri);
        photoView.setImageBitmap(b);
    }*/

    @Override
    public void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_GALLERY);
        } catch (ActivityNotFoundException e) {
        }
    }

    private void init() {

        showCropping();
        Bitmap b = getBitmap(mImageUri);
        Drawable bitmap = new BitmapDrawable(getResources(), b);
        int h = bitmap.getIntrinsicHeight();
        int w = bitmap.getIntrinsicWidth();
        final float cropWindowWidth = Edge.getWidth();
        final float cropWindowHeight = Edge.getHeight();
        if (h <= w) {
            minScale = (cropWindowHeight + 1f) / h;
        } else if (w < h) {
            minScale = (cropWindowWidth + 1f) / w;
        }

        photoView.setMaximumScale(minScale * 9);
        photoView.setMediumScale(minScale * 6);
        photoView.setMinimumScale(minScale);
        photoView.setImageDrawable(bitmap);
        photoView.setScale(minScale);


    }


    private Bitmap getBitmap(Uri uri) {
        InputStream in = null;
        Bitmap returnedBitmap = null;
        try {
            in = mContentResolver.openInputStream(uri);
            //Decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int scale = 1;
            if (o.outHeight > IMAGE_MAX_SIZE || o.outWidth > IMAGE_MAX_SIZE) {
                scale = (int) Math.pow(2, (int) Math.round(Math.log(IMAGE_MAX_SIZE / (double) Math.max(o.outHeight, o.outWidth)) / Math.log(0.5)));
            }

            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            in = mContentResolver.openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, o2);
            in.close();
            returnedBitmap = fixOrientationBugOfProcessedBitmap(bitmap);
            return returnedBitmap;
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
        }
        return null;
    }

    private Bitmap fixOrientationBugOfProcessedBitmap(Bitmap bitmap) {
        try {
            if (getCameraPhotoOrientation(this, Uri.parse(mFileTemp.getPath())) == 0) {
                return bitmap;
            } else {
                Matrix matrix = new Matrix();
                matrix.postRotate(getCameraPhotoOrientation(this, Uri.parse(mFileTemp.getPath())));
                // recreate the new Bitmap and set it back
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void saveAndUploadImage() {
        boolean saved = saveOutput();

        if (saved) {

            hideCropping();
        } else {
        }


    }

    private boolean saveOutput() {
        Bitmap croppedImage = getCroppedImage();
        new ImageScanner().execute(croppedImage);
        imgImage.setImageBitmap(croppedImage);



//        if (mSaveUri != null) {
//            OutputStream outputStream = null;
//            try {
//                outputStream = mContentResolver.openOutputStream(mSaveUri);
//                if (outputStream != null) {
//                    croppedImage.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
//
//                }
//            } catch (IOException ex) {
//                ex.printStackTrace();
//                return false;
//            } finally {
//                if (outputStream != null) {
//                    try {
//                        outputStream.close();
//                    } catch (Throwable t) {
//                    }
//                }
//            }
//        } else {
//            return false;
//        }
//        croppedImage.recycle();
        return true;
    }

    CircleOverLayView circleOverLayView;

    private Bitmap getCurrentDisplayedImage() {
        Bitmap result = Bitmap.createBitmap(photoView.getWidth(), photoView.getHeight(), Bitmap.Config.RGB_565);
        Canvas c = new Canvas(result);
        photoView.draw(c);
        return result;
    }

    public Bitmap getCroppedImage() {
        int posX, posY;
        circleOverLayView = (CircleOverLayView) findViewById(R.id.test);


        Bitmap mCurrentDisplayedBitmap = getCurrentDisplayedImage();
        Rect displayedImageRect = ImageViewUtil.getBitmapRectCenterInside(mCurrentDisplayedBitmap, photoView);

        // Get the scale factor between the actual Bitmap dimensions and the
        // displayed dimensions for width.
        float actualImageWidth = mCurrentDisplayedBitmap.getWidth();
        float displayedImageWidth = displayedImageRect.width();
        float scaleFactorWidth = actualImageWidth / displayedImageWidth;

        // Get the scale factor between the actual Bitmap dimensions and the
        // displayed dimensions for height.
        float actualImageHeight = mCurrentDisplayedBitmap.getHeight();
        float displayedImageHeight = displayedImageRect.height();
        float scaleFactorHeight = actualImageHeight / displayedImageHeight;

        float overlayHeight = (Edge.getWidth() / 3);
        float midpoint = overlayHeight / 2;
        float y = (Edge.getHeight() / 2) - midpoint;


        // Get crop window position relative to the displayed image.
        float cropWindowX = 0;
        float cropWindowY = y;
        float cropWindowWidth = Edge.getWidth();
        float cropWindowHeight = Edge.getHeight();

        // Scale the crop window position to the actual size of the Bitmap.
        float actualCropX = cropWindowX * scaleFactorWidth;
        float actualCropY = cropWindowY * scaleFactorHeight;
        float actualCropWidth = cropWindowWidth * scaleFactorWidth;
        float actualCropHeight = 200f;

        // Crop the subset from the original Bitmap.
        return Bitmap.createBitmap(mCurrentDisplayedBitmap, (int) actualCropX, (int) actualCropY, (int) actualCropWidth, (int) actualCropHeight);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);
        createTempFile();
        showCropping();
        if (requestCode == REQUEST_CODE_TAKE_PICTURE && resultCode == RESULT_OK) {

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                hideCropping();
                imgImage.setVisibility(View.VISIBLE);
                imgImage.setImageResource(R.mipmap.ic_launcher);

                Toast.makeText(this, "NO permission on picture", Toast.LENGTH_SHORT).show();

                return;
            }


            mImagePath = mFileTemp.getPath();
            mSaveUri = Utils.getImageUri(mImagePath);
            mImageUri = Utils.getImageUri(mImagePath);
            init();
        } else if (requestCode == REQUEST_CODE_PICK_GALLERY && resultCode == RESULT_OK) {


            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {


                hideCropping();
                imgImage.setVisibility(View.VISIBLE);
                imgImage.setImageResource(R.mipmap.ic_launcher);
                Toast.makeText(this, "NO permission on Storage", Toast.LENGTH_SHORT).show();

                //code for default image
                return;
            }

            try {
                InputStream inputStream = getContentResolver().openInputStream(result.getData());
                FileOutputStream fileOutputStream = new FileOutputStream(mFileTemp);
                copyStream(inputStream, fileOutputStream);
                fileOutputStream.close();
                inputStream.close();
                mImagePath = mFileTemp.getPath();
                mSaveUri = Utils.getImageUri(mImagePath);
                mImageUri = Utils.getImageUri(mImagePath);
                init();
            } catch (Exception e) {
            }
        } else {

            hideCropping();

            imgImage.setVisibility(View.VISIBLE);


        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.btnChooseGallery:
                mImagePath = null;//do again
                onGetImages(ConstantsImageCrop.IntentExtras.ACTION_GALLERY);
                break;
            case R.id.btnTakePicture:
                mImagePath = null;
                onGetImages(ConstantsImageCrop.IntentExtras.ACTION_CAMERA);
                break;

            case R.id.doneCrop:
                if (mImagePath != null) saveAndUploadImage();

                break;


        }
    }

    public class ImageScanner extends AsyncTask<Bitmap, Void, String> {

        ProgressDialog pdialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

//            pdialog = new ProgressDialog(getApplicationContext());
//            pdialog.setMessage("Scanning.......");
//            pdialog.show();

        }


        @Override
        protected String doInBackground(Bitmap... bitmaps) {

//            String DATA_PATH = Environment.getExternalStorageDirectory().toString();
            Bitmap bitmap = bitmaps[0];
            String BLACK_LIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmopqrstuvwxyz!@#$%^&*()_+=-qwertyuiop[]}{POIU\" +\n" +
                    "                        \"YTRWQasdASDfghFGHjklJKLl;L:'\\\"\\\\|~`xcvXCVbnmBNM,./<>?[]^_`{|}~ÇüéâäàåçêëèïîìæÆôöòûùÿ¢£¥PƒáíóúñÑ¿¬½¼¡«»¦ßµ±°•·²€„…†‡ˆ‰Š‹Œ‘’“”–—˜™š›œŸ¨©®¯³´¸¹¾ÀÁÂÃÄÅÈÉÊËÌÍÎÏÐÒÓÔÕÖ×ØÙÚÛÜÝÞãðõ÷øüýþ";

            TessBaseAPI tessBaseApi = new TessBaseAPI();
            String scanResult = null;

            try {
//                String stringToOmmit = "!@#$%^&*()_+=-qwertyuiop[]}{POIU" +
//                        "YTRWQasdASDfghFGHjklJKLl;L:'\"\\|~`xcvXCVbnmBNM,./<>?";

                tessBaseApi.init(mImagePath, "eng");
                 tessBaseApi.setImage(bitmap);
                tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, BLACK_LIST);
                scanResult = tessBaseApi.getUTF8Text();
                tessBaseApi.end();
            } catch (Exception e) {
                return e.getMessage();
            }


            return scanResult;
        }


        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);


            scanResultView.setText(s);
//            pdialog.dismiss();
        }
    }

}