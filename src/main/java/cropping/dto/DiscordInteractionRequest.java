package cropping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Discord Interaction Request - สำหรับรับ event จาก Discord
 */
public class DiscordInteractionRequest {

    @JsonProperty("id")
    private String id;

    @JsonProperty("application_id")
    private String applicationId;

    @JsonProperty("type")
    private int type;

    @JsonProperty("data")
    private InteractionData data;

    @JsonProperty("guild_id")
    private String guildId;

    @JsonProperty("channel_id")
    private String channelId;

    @JsonProperty("user") // สำหรับ DM
    private User user;

    @JsonProperty("member") // สำหรับ Guild message
    private Member member;

    @JsonProperty("token")
    private String token;

    @JsonProperty("version")
    private int version;

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public InteractionData getData() { return data; }
    public void setData(InteractionData data) { this.data = data; }

    public String getGuildId() { return guildId; }
    public void setGuildId(String guildId) { this.guildId = guildId; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Member getMember() { return member; }
    public void setMember(Member member) { this.member = member; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    /**
     * ดึง userId - รองรับทั้ง DM (user) และ Guild (member)
     */
    public String getUserId() {
        if (user != null) {
            return user.getId();
        }
        if (member != null && member.getUser() != null) {
            return member.getUser().getId();
        }
        return null;
    }

    // ========== Inner Classes ==========

    public static class InteractionData {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private int type;

        @JsonProperty("options")
        private List<Option> options;

        @JsonProperty("custom_id")
        private String customId;

        @JsonProperty("component_type")
        private int componentType;

        // Getters and Setters

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getType() { return type; }
        public void setType(int type) { this.type = type; }

        public List<Option> getOptions() { return options; }
        public void setOptions(List<Option> options) { this.options = options; }

        public String getCustomId() { return customId; }
        public void setCustomId(String customId) { this.customId = customId; }

        public int getComponentType() { return componentType; }
        public void setComponentType(int componentType) { this.componentType = componentType; }

        public String getOptionValue(String optionName) {
            if (options == null) return null;
            for (Option opt : options) {
                if (optionName.equals(opt.getName())) {
                    return opt.getValue();
                }
            }
            return null;
        }
    }

    public static class Option {
        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private int type;

        @JsonProperty("value")
        private String value;

        // Getters and Setters

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getType() { return type; }
        public void setType(int type) { this.type = type; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static class User {
        @JsonProperty("id")
        private String id;

        @JsonProperty("username")
        private String username;

        @JsonProperty("discriminator")
        private String discriminator;

        @JsonProperty("global_name")
        private String globalName;

        // Getters and Setters

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getDiscriminator() { return discriminator; }
        public void setDiscriminator(String discriminator) { this.discriminator = discriminator; }

        public String getGlobalName() { return globalName; }
        public void setGlobalName(String globalName) { this.globalName = globalName; }
    }

    public static class Member {
        @JsonProperty("user")
        private User user;

        @JsonProperty("nick")
        private String nick;

        @JsonProperty("roles")
        private List<String> roles;

        // Getters and Setters

        public User getUser() { return user; }
        public void setUser(User user) { this.user = user; }

        public String getNick() { return nick; }
        public void setNick(String nick) { this.nick = nick; }

        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }
}
