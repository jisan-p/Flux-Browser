package com.flux.browser.feature;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Compiles a bounded set of domain rules for WebKit, never executes filter-list code. */
public final class ContentRules {
    public static final String SOURCE="https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt";
    private static final List<String> STARTER=List.of("doubleclick.net","googlesyndication.com","googleadservices.com","google-analytics.com","adnxs.com","adsrvr.org","scorecardresearch.com","taboola.com","outbrain.com","criteo.com","connect.facebook.net");
    private ContentRules(){}
    public static String starterRules(){return build(STARTER,List.of());}
    public static String build(Collection<String> domains,Collection<String> exceptions){
        JsonArray rules=new JsonArray();
        for(String domain:domains){if(!valid(domain))continue;
            JsonObject trigger=new JsonObject();trigger.addProperty("url-filter","^https?://([^/]+\\.)?"+domain.replace(".","\\.")+"[/:]");
            JsonArray types=new JsonArray();types.add("third-party");trigger.add("load-type",types);
            add(rules,trigger,"block");
        }
        for(String host:exceptions){if(!valid(host))continue;
            JsonObject trigger=new JsonObject();trigger.addProperty("url-filter",".*");JsonArray domainsArray=new JsonArray();domainsArray.add("*"+host);trigger.add("if-domain",domainsArray);add(rules,trigger,"ignore-previous-rules");
        }
        return rules.toString();
    }
    private static void add(JsonArray rules,JsonObject trigger,String type){JsonObject r=new JsonObject(),a=new JsonObject();a.addProperty("type",type);r.add("trigger",trigger);r.add("action",a);rules.add(r);}
    private static boolean valid(String d){return d!=null && d.length()<254 && d.matches("[a-z0-9]+(?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,}");}
    public static List<String> load(Path directory)throws Exception{
        Path file=directory.resolve("blocking-domains.txt");
        if(!Files.isRegularFile(file))return STARTER;
        if(Files.size(file)>3_000_000)throw new IllegalStateException("Blocklist is too large");
        return Files.readAllLines(file).stream().filter(ContentRules::valid).limit(30000).toList();
    }
    public static int update(Path directory)throws Exception{
        try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()){
        var response=client.send(HttpRequest.newBuilder(URI.create(SOURCE)).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;try(var in=response.body()){bytes=in.readNBytes(8_000_001);}
        if(response.statusCode()!=200 || bytes.length>8_000_000)throw new IllegalStateException("Blocklist update unavailable; keeping existing rules.");
        Set<String> domains=new LinkedHashSet<>(STARTER),allow=new HashSet<>();
        for(String raw:new String(bytes,java.nio.charset.StandardCharsets.UTF_8).split("\\R")){
            if(raw.startsWith("@@||") && raw.endsWith("^"))allow.add(raw.substring(4,raw.length()-1));
            else if(raw.startsWith("||")&&raw.endsWith("^")&&domains.size()<30000){String d=raw.substring(2,raw.length()-1);if(valid(d))domains.add(d);}
        }
        domains.removeAll(allow);
        if(domains.size()<100)throw new IllegalStateException("Unexpected filter format; keeping existing rules.");
        FeatureStore.writeAtomic(directory.resolve("blocking-domains.txt"),String.join("\n",domains));return domains.size();
        }
    }
}
