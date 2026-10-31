package org.simpleweather.app;

/** XML-backed text shared by Android screens and platform-independent models.
 * Install before constructing any models. Stable data identifiers use raw(), never a translation.
 */
public final class UiText {
    public interface Provider {String get(String key);String label(String identity);}
    private static volatile Provider provider;
    private UiText(){}
    public static void install(Provider value){if(value==null)throw new IllegalArgumentException();provider=value;}
    public static String get(String key){Provider p=provider;if(p==null)throw new IllegalStateException("Text resources not initialized");return p.get(key);}
    public static String raw(String key){return get(key);}
    public static String label(String identity){Provider p=provider;if(p==null)throw new IllegalStateException("Text resources not initialized");return p.label(identity);}
}
