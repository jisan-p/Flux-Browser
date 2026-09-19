package com.flux.browser.feature;

import com.google.gson.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Optional installed CLI adapters. Only the explicitly selected item is fetched; no vault enumeration. */
public final class PasswordProviders {
    public record Login(String username,String password) {}
    private PasswordProviders(){}
    public static String origin(String address){
        URI u=URI.create(address);
        if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getPort()==0||u.getPort()>65535)throw new IllegalArgumentException("Logins require an HTTPS page.");
        return "https://"+u.getHost().toLowerCase(Locale.ROOT)+(u.getPort()>0 && u.getPort()!=443?":"+u.getPort():"");
    }
    public static Login fetch(String provider,String item,String expectedOrigin) throws Exception {
        if(!item.matches("[a-zA-Z0-9-]{16,64}"))throw new IllegalArgumentException("Enter the item's ID from your password manager.");
        if(!List.of("Bitwarden CLI","1Password CLI").contains(provider))throw new IllegalArgumentException("Choose a supported password provider.");
        boolean bw=provider.equals("Bitwarden CLI");String binary=bw?"bw":"op";
        Path executable=List.of(Path.of("/opt/homebrew/bin",binary),Path.of("/usr/local/bin",binary)).stream().filter(Files::isExecutable).findFirst()
            .orElseThrow(()->new IllegalStateException("Install and sign in to the " + provider + " before using this provider."));
        Process process=new ProcessBuilder(bw?List.of(executable.toString(),"get","item",item):List.of(executable.toString(),"item","get",item,"--format=json","--reveal"))
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var read=CompletableFuture.supplyAsync(()->{try(var stream=process.getInputStream()){return stream.readNBytes(1_000_001);}catch(Exception e){throw new CompletionException(e);}});
        try{
            if(!process.waitFor(30,TimeUnit.SECONDS))throw new IllegalStateException("Password manager timed out. Unlock it and retry.");
            byte[] data=read.get(3,TimeUnit.SECONDS);
            if(process.exitValue()!=0||data.length>1_000_000)throw new IllegalStateException("Password manager unavailable or locked. Sign in/unlock it outside Flux and retry.");
            JsonObject object;
            try { object=JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject(); } finally { Arrays.fill(data,(byte)0); }
            String user="",password="";boolean matched=false;
            if(bw){
                JsonObject login=object.getAsJsonObject("login");
                for(JsonElement e:login.getAsJsonArray("uris"))if(matches(e.getAsJsonObject().get("uri"),expectedOrigin))matched=true;
                user=login.get("username").getAsString();password=login.get("password").getAsString();
            }else{
                for(JsonElement e:object.getAsJsonArray("urls"))if(matches(e.getAsJsonObject().get("href"),expectedOrigin))matched=true;
                for(JsonElement e:object.getAsJsonArray("fields")){JsonObject f=e.getAsJsonObject();String id=f.has("id")?f.get("id").getAsString():"";
                    if(id.equals("username"))user=f.get("value").getAsString();if(id.equals("password"))password=f.get("value").getAsString();}
            }
            if(!matched||password.isEmpty())throw new IllegalArgumentException("That login has no password or does not match this exact HTTPS origin.");
            return new Login(user,password);
        }catch(RuntimeException e){throw new IllegalStateException("Password manager item unavailable, invalid, or not matched to this exact HTTPS origin. Unlock the provider and check the item ID.");}
        finally{process.destroyForcibly();}
    }
    private static boolean matches(JsonElement url,String origin){try{return origin(url.getAsString()).equals(origin);}catch(Exception ignored){return false;}}
    public static String fillScript(String origin,Login login){
        return "(() => {if(location.origin!=="+FeatureStore.JSON.toJson(origin)+")return false;"
            +"const p=Array.from(document.querySelectorAll('input[type=password]')).find(e=>e.getClientRects().length && !e.disabled && !e.readOnly);if(!p)return false;"
            +"const form=p.form||document;const u=Array.from(form.querySelectorAll('input')).find(e=>e.getClientRects().length && !e.disabled && !e.readOnly && (e.autocomplete==='username'||e.type==='email'||e.type==='text'));"
            +"const set=(e,v)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));};"
            +"if(u)set(u,"+FeatureStore.JSON.toJson(login.username())+");set(p,"+FeatureStore.JSON.toJson(login.password())+");return true;})()";
    }
}
