# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools directory.

# Keep TensorFlow Lite model classes
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# Keep Room entities
-keep class com.example.museumguide.model.** { *; }

# Keep Parcelable classes
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
