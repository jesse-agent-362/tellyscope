# Project-specific R8 rules go here.

# Kadb makes its host certificate through the Bouncy Castle JCA provider, which
# registers its algorithms as class-name strings (".../RSA$Mappings") and loads
# them reflectively. R8 can't see those, so it renames or strips them. The
# provider packages are where every by-name lookup points; the crypto they call
# is ordinary code R8 can follow.
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-dontwarn org.bouncycastle.**

# Kadb itself: pairing reaches Conscrypt and hidden APIs by name. Small enough
# to keep whole rather than chase each reflective call.
-keep class com.flyfishxu.kadb.** { *; }
