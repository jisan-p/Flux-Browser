package com.flux.browser.feature;

import java.math.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Declarative search plugins: no downloaded code and no page privileges. */
public final class SearchTools {
    private SearchTools() {}
    public static String resolve(String input,List<FeatureStore.SearchProvider> providers) {
        String[] parts=input.trim().split("\\s+",2);
        for(var p:providers)if(p.keyword().equals(parts[0])){
            if(parts.length<2)throw new IllegalArgumentException("Enter a query after " + p.keyword());
            validate(p);
            return p.template().replace("{query}",URLEncoder.encode(parts[1],StandardCharsets.UTF_8));
        }
        return input;
    }
    public static void validate(FeatureStore.SearchProvider p) {
        if(!p.keyword().matches("![a-z0-9]{1,16}") || p.name().isBlank() || p.name().length()>60 || p.template().length()>2048
            || !p.template().contains("{query}"))throw new IllegalArgumentException("Use a !keyword, a name, and an HTTPS URL containing {query}.");
        URI u=URI.create(p.template().replace("{query}","test"));
        if(!"https".equals(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null)throw new IllegalArgumentException("Search provider must use HTTPS without embedded credentials.");
    }
    public static String answer(String input) {
        if(!input.trim().startsWith("="))return "";
        try { return "= " + new Arithmetic(input.trim().substring(1)).parse().stripTrailingZeros().toPlainString(); }
        catch(RuntimeException e){return "Use = followed by arithmetic, e.g. = (12 + 8) / 4";}
    }
    private static final class Arithmetic {
        final String s; int i,depth;
        Arithmetic(String s){if(s.length()>200)throw new IllegalArgumentException();this.s=s;}
        BigDecimal parse(){BigDecimal n=expr();space();if(i!=s.length())throw new IllegalArgumentException();return n;}
        void space(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
        boolean take(char c){space();if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}
        BigDecimal expr(){BigDecimal a=term();while(true){if(take('+'))a=a.add(term());else if(take('-'))a=a.subtract(term());else return a;}}
        BigDecimal term(){BigDecimal a=factor();while(true){if(take('*'))a=a.multiply(factor(),MathContext.DECIMAL64);else if(take('/'))a=a.divide(factor(),MathContext.DECIMAL64);else return a;}}
        BigDecimal factor(){if(++depth>20)throw new IllegalArgumentException();try{
            if(take('-'))return factor().negate();if(take('+'))return factor();
            if(take('(')){BigDecimal n=expr();if(!take(')'))throw new IllegalArgumentException();return n;}
            space();int a=i;while(i<s.length()&&(Character.isDigit(s.charAt(i))||s.charAt(i)=='.'))i++;
            return new BigDecimal(s.substring(a,i));
        }finally{depth--;}}
    }
}
