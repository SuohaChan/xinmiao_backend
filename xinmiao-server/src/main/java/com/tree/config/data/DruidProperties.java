package com.tree.config.data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


/**
 * @author SuohaChan
 * @data 2025/9/9
 */



@Component
@ConfigurationProperties(prefix = "spring.datasource.druid")
public class DruidProperties {
    private String url;
    private String username;
    private String password;

    // getter 和 setter
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
