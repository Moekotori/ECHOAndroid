# Rhino's interpreter, VM bridge and lazy standard constructors are loaded by
# string name. Keep these entry points without retaining the entire Rhino jar.
-keep,allowoptimization class org.mozilla.javascript.Interpreter {
    public <init>();
}
-keep,allowoptimization class org.mozilla.javascript.jdk18.VMBridge_jdk18 {
    public <init>();
}
-keep,allowoptimization class org.mozilla.javascript.regexp.RegExpImpl {
    public <init>();
}
-keep,allowoptimization class org.mozilla.javascript.NativeContinuation {
    public static void init(...);
}
-keep,allowoptimization class org.mozilla.javascript.regexp.NativeRegExp {
    public static void init(...);
}
-keep,allowoptimization class org.mozilla.javascript.typedarrays.Native* {
    public <init>();
    public static void init(...);
}

# ScriptRuntime resolves these API types through Kit.classOrNull as well.
-keepnames class org.mozilla.javascript.Context
-keepnames class org.mozilla.javascript.ContextFactory
-keepnames interface org.mozilla.javascript.Function
-keepnames class org.mozilla.javascript.ScriptableObject

# Rhino's optional Java-object JSON converter uses desktop JavaBeans. The
# plugin bridge creates JS objects explicitly and its WrapFactory rejects Java.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
