/*
 * Copyright 2016 John Grosh (jagrosh).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jdautilities.examples.command.AboutCommand;
import com.jagrosh.jmusicbot.commands.admin.*;
import com.jagrosh.jmusicbot.commands.dj.*;
import com.jagrosh.jmusicbot.commands.general.*;
import com.jagrosh.jmusicbot.commands.music.*;
import com.jagrosh.jmusicbot.commands.owner.*;
import com.jagrosh.jmusicbot.entities.Prompt;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.security.auth.login.LoginException;
import java.awt.Color;
import java.util.*;
import ch.qos.logback.classic.Level;

/**
 * Main class for JMusicBot with dynamic bot creation for master and slave bots
 *
 * @author
 */
public class JMusicBot {
    public final static Logger LOG = LoggerFactory.getLogger(JMusicBot.class);
    public final static Permission[] RECOMMENDED_PERMS = {Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ADD_REACTION,
            Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_MANAGE, Permission.MESSAGE_EXT_EMOJI,
            Permission.VOICE_CONNECT, Permission.VOICE_SPEAK, Permission.NICKNAME_CHANGE};
    public final static GatewayIntent[] INTENTS = {GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_VOICE_STATES};

    public static void main(String[] args) {
        if (args.length > 0 && args[0].toLowerCase().equals("generate-config")) {
            BotConfig.writeDefaultConfig();
            return;
        }
        startBot();
    }

    private static void startBot() {

        Dotenv dotenv = Dotenv.load();
        // create prompt to handle startup
        Prompt prompt = new Prompt("JMusicBot");

        // startup checks
        OtherUtil.checkVersion(prompt);
        OtherUtil.checkJavaVersion(prompt);

        // load config (shared config for all bots or configure separately if needed)
        BotConfig config = new BotConfig(prompt);
        config.load();
        if (!config.isValid()) return;
        LOG.info("Loaded config from " + config.getConfigLocation());

        // set log level from config
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(
                Level.toLevel(config.getLogLevel(), Level.INFO));

        // set up the listener
        EventWaiter waiter = new EventWaiter();
        SettingsManager settings = new SettingsManager();

        // List of tokens for master and slave bots
        List<String> tokenList = new ArrayList<>();
        tokenList.add(dotenv.get("DISCORD_MASTER_TOKEN"));   // First bot is the master
        tokenList.add(dotenv.get("DISCORD_SLAVE_TOKEN_1"));  // First slave bot
        tokenList.add(dotenv.get("DISCORD_SLAVE_TOKEN_2"));  // Second slave bot

        // Loop through tokens and create corresponding bot instances
        List<Bot> slaveBots = new ArrayList<>();
        Bot masterBot = null;

        for (String token : tokenList) {
            try {
                Bot bot = new Bot(waiter, config, settings);
                CommandClient client = createCommandClient(config, settings, bot);

                JDA jda = JDABuilder.create(token, Arrays.asList(INTENTS))
                        .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
                        .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOTE, CacheFlag.ONLINE_STATUS)
                        .setActivity(config.isGameNone() ? null : Activity.playing("loading..."))
                        .setStatus(config.getStatus() == OnlineStatus.INVISIBLE || config.getStatus() == OnlineStatus.OFFLINE
                                ? OnlineStatus.INVISIBLE : OnlineStatus.DO_NOT_DISTURB)
                        //.addEventListeners(client, waiter, createAppropriateListener(bot, token))
                        .setBulkDeleteSplittingEnabled(true)
                        .build();

                bot.setJDA(jda);

                // If it's the master bot, track it
                if (isMasterBot(token)) {
                    masterBot = bot;
                } else {
                    // If it's a slave bot, add it to the slaveBots list
                    slaveBots.add(bot);
                }

                // Check for unsupported bot
                String unsupportedReason = OtherUtil.getUnsupportedBotReason(jda);
                if (unsupportedReason != null) {
                    prompt.alert(Prompt.Level.ERROR, "JMusicBot", "JMusicBot cannot be run on this Discord bot: " + unsupportedReason);
                    jda.shutdown();
                    System.exit(1);
                }

            } catch (LoginException | IllegalArgumentException | ErrorResponseException ex) {
                LOG.error("Failed to log in with the token: " + token, ex);
            }
        }

// Register the MasterBotListener only after all bots are initialized
        if (masterBot != null) {
            masterBot.getJDA().addEventListener(new MasterBotListener(masterBot, slaveBots));
        } else {
            LOG.error("No master bot initialized.");
        }

    }

    // Create appropriate listener for master or slave bots
//    private static Object createAppropriateListener(Bot bot, String token) {
//        // Assuming the first token is for the master bot
//        if (isMasterBot(token)) {
//            return new MasterListener(bot);  // Master bot listener
//        } else {
//            return new SlaveListener(bot);   // Slave bot listener
//        }
//    }

    // Check if the bot is the master bot based on its token
    private static boolean isMasterBot(String token) {
        Dotenv dotenv = Dotenv.load();
        return token.equals(dotenv.get("DISCORD_MASTER_TOKEN"));  // Replace with your actual master bot token
    }

    private static CommandClient createCommandClient(BotConfig config, SettingsManager settings, Bot bot) {
        // Instantiate about command
        AboutCommand aboutCommand = new AboutCommand(Color.BLUE.brighter(),
                "a music bot that is [easy to host yourself!](https://github.com/jagrosh/MusicBot) (v" + OtherUtil.getCurrentVersion() + ")",
                new String[]{"High-quality music playback", "FairQueue™ Technology", "Easy to host yourself"},
                RECOMMENDED_PERMS);
        aboutCommand.setIsAuthor(false);
        aboutCommand.setReplacementCharacter("\uD83C\uDFB6"); // 🎶

        // Set up the command client
        CommandClientBuilder cb = new CommandClientBuilder()
                .setPrefix(config.getPrefix())
                .setAlternativePrefix(config.getAltPrefix())
                .setOwnerId(Long.toString(config.getOwnerId()))
                .setEmojis(config.getSuccess(), config.getWarning(), config.getError())
                .setHelpWord(config.getHelp())
                .setLinkedCacheSize(200)
                .setGuildSettingsManager(settings)
                .addCommands(aboutCommand,
                        new SettingsCmd(bot),
                        new LyricsCmd(bot),
                        new NowplayingCmd(bot),
//                        new PlayCmd(bot),
                        new PlaylistsCmd(bot),
                        new QueueCmd(bot),
                        new RemoveCmd(bot),
//                        new SearchCmd(bot),
//                        new SCSearchCmd(bot),
                        new SeekCmd(bot),
                        new ShuffleCmd(bot),
                        new SkipCmd(bot),
                        new ForceRemoveCmd(bot),
                        new ForceskipCmd(bot),
                        new MoveTrackCmd(bot),
                        new PauseCmd(bot),
//                        new PlaynextCmd(bot),
                        new RepeatCmd(bot),
                        new SkiptoCmd(bot),
                        new StopCmd(bot),
                        new VolumeCmd(bot),
                        new PrefixCmd(bot),
                        new QueueTypeCmd(bot),
                        new SetdjCmd(bot),
                        new SkipratioCmd(bot),
                        new SettcCmd(bot),
                        new SetvcCmd(bot),
                        new AutoplaylistCmd(bot),
                        new DebugCmd(bot),
                        new PlaylistCmd(bot),
                        new SetavatarCmd(bot),
                        new SetgameCmd(bot),
                        new SetnameCmd(bot),
                        new SetstatusCmd(bot),
                        new ShutdownCmd(bot)
                );

        // Enable eval if applicable
        if (config.useEval()) {
            cb.addCommand(new EvalCmd(bot));
        }

        // Set status if set in config
        if (config.getStatus() != OnlineStatus.UNKNOWN) {
            cb.setStatus(config.getStatus());
        }

        // Set game
        if (config.getGame() == null) {
            cb.useDefaultGame();
        } else if (config.isGameNone()) {
            cb.setActivity(null);
        } else {
            cb.setActivity(config.getGame());
        }

        return cb.build();
    }
}
