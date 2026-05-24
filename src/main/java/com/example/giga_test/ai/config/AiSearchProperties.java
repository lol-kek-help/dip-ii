package com.example.giga_test.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
@ConfigurationProperties(prefix = "ai.search")
public class AiSearchProperties {
    private double minRelevanceScore = 0.15;
    private double vectorWeight = 0.75;
    private double lexicalWeight = 0.25;
    private double tokenBoostStep = 0.05;
    private double tokenBoostCap = 0.2;
    private int ftsLimit = 25;
    private int vectorLimit = 25;
    private int rerankLimit = 10;
    private int finalLimit = 5;
    private int ragLimit = 3;
    private Map<String, List<String>> domainTokens = defaultDomainTokens();

    private static Map<String, List<String>> defaultDomainTokens() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("ACCESS", List.of("vpn", "парол", "доступ", "ad", "mfa", "учет", "логин"));
        m.put("EMAIL", List.of("почт", "outlook", "exchange", "smtp", "imap"));
        m.put("NETWORK", List.of("сеть", "dns", "маршрут", "mikrotik", "ipsec", "proxy"));
        m.put("WORKPLACE", List.of("принтер", "сканер", "рабоч", "ноутбук", "пк"));
        m.put("ERP", List.of("1с", "sap", "oracle", "hrm", "tableau"));
        return m;
    }

    public Set<String> allTokens() {
        Set<String> out = new HashSet<>();
        domainTokens.values().forEach(out::addAll);
        return out;
    }

    public double getMinRelevanceScore() { return minRelevanceScore; }
    public void setMinRelevanceScore(double minRelevanceScore) { this.minRelevanceScore = minRelevanceScore; }
    public double getVectorWeight() { return vectorWeight; }
    public void setVectorWeight(double vectorWeight) { this.vectorWeight = vectorWeight; }
    public double getLexicalWeight() { return lexicalWeight; }
    public void setLexicalWeight(double lexicalWeight) { this.lexicalWeight = lexicalWeight; }
    public double getTokenBoostStep() { return tokenBoostStep; }
    public void setTokenBoostStep(double tokenBoostStep) { this.tokenBoostStep = tokenBoostStep; }
    public double getTokenBoostCap() { return tokenBoostCap; }
    public void setTokenBoostCap(double tokenBoostCap) { this.tokenBoostCap = tokenBoostCap; }
    public int getFtsLimit() { return ftsLimit; }
    public void setFtsLimit(int ftsLimit) { this.ftsLimit = ftsLimit; }
    public int getVectorLimit() { return vectorLimit; }
    public void setVectorLimit(int vectorLimit) { this.vectorLimit = vectorLimit; }
    public int getRerankLimit() { return rerankLimit; }
    public void setRerankLimit(int rerankLimit) { this.rerankLimit = rerankLimit; }
    public int getFinalLimit() { return finalLimit; }
    public void setFinalLimit(int finalLimit) { this.finalLimit = finalLimit; }
    public int getRagLimit() { return ragLimit; }
    public void setRagLimit(int ragLimit) { this.ragLimit = ragLimit; }
    public Map<String, List<String>> getDomainTokens() { return domainTokens; }
    public void setDomainTokens(Map<String, List<String>> domainTokens) { this.domainTokens = domainTokens; }
}

