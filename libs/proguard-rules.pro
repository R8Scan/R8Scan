-allowaccessmodification

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses


-libraryjars ../../../libs/android.jar
# -printmapping info/mapping.txt
# -printseeds info/seeds.txt
# -printusage info/usage.txt

-dontobfuscate
# -dontoptimize
# -dontshrink
-keep class Test { public static void main(java.lang.String[]); }