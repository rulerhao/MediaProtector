# Consumer rules for media-crypto library
# These rules are applied when this library is consumed by the app

# Keep the public API of HeaderObfuscator
-keep class com.rulerhao.media_protector.crypto.HeaderObfuscator {
    public <methods>;
    public static <methods>;
}
