# SMBJ finds its connection/session cleanup callbacks through MBassador.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers,allowoptimization,includedescriptorclasses class com.hierynomus.smbj.** {
    @net.engio.mbassy.listener.Handler <methods>;
}
-keep,allowoptimization class * implements net.engio.mbassy.dispatch.IHandlerInvocation {
    public <init>(net.engio.mbassy.subscription.SubscriptionContext);
}

# SMBJ disables SPNEGO on Android; Android credentials use NTLM/guest instead.
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# SMBJ's @Handler callbacks have no EL filter expressions. These optional
# MBassador integrations are desktop dependencies, not Android runtime APIs.
-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper
