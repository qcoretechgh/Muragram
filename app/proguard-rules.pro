# Muragram Proguard Rules
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.dycrd.muragram.** { *; }
-keep class org.json.** { *; }

# Keep network related stuff
-keepclassmembers class * extends android.os.AsyncTask {
    protected <methods>;
}

# Optimization
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
