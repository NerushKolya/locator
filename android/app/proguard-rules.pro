# kotlinx.serialization keeps its generated serializers through the consumer rules of the library.
# The OkHttp engine of Ktor ships its own consumer rules.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
