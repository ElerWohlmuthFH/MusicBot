package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.SlavePlayCommand;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.stream.Collectors;

public class MasterBotListener extends ListenerAdapter {
    private final Bot masterBot;  // Reference to the master bot instance
    private final List<Bot> slaveBots;  // List of slave bots

    public MasterBotListener(Bot masterBot, List<Bot> slaveBots) {
        this.masterBot = masterBot;
        this.slaveBots = slaveBots;  // This should contain the Bot instances for each slave bot
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String message = event.getMessage().getContentRaw();
        if (message.startsWith("!play")) {
            Guild guild = event.getGuild();  // Get the current guild
            event.getChannel().sendMessage("Master bot received the play command. Selecting a slave to handle it...").queue();

            // Select a slave bot that is not already in a voice channel
            Bot availableSlave = findAvailableSlave(guild);
            if (availableSlave != null) {
                // Forward the command to the selected slave bot for execution
                instructSlaveToPlay(availableSlave, event);
            } else {
                event.getChannel().sendMessage("No available slave bots to handle the command.").queue();
            }
        }
    }

    private Bot findAvailableSlave(Guild guild) {
        // Filter out bots that are already in a voice channel
        List<Bot> availableSlaveBots = slaveBots.stream()
                .filter(slaveBot -> {
                    // Get the slave bot's SelfMember in the specific guild
                    Guild slaveGuild = slaveBot.getJDA().getGuildById(guild.getId());
                    if (slaveGuild == null) {
                        // If the slave bot is not in the same guild, skip it
                        return false;
                    }
                    Member selfMember = slaveGuild.getSelfMember();
                    // Check if the bot is not currently connected to a voice channel
                    return selfMember.getVoiceState() != null && !selfMember.getVoiceState().inVoiceChannel();
                })
                .collect(Collectors.toList());

        if (availableSlaveBots.isEmpty()) {
            System.out.println("No available slave bots in this guild.");
            return null;  // Return null if no available slaves
        }

        // Randomly select an available slave bot
        Bot selectedSlaveBot = availableSlaveBots.get((int) (Math.random() * availableSlaveBots.size()));
        System.out.println("Selected Slave Bot: " + selectedSlaveBot.getJDA().getSelfUser().getName());
        return selectedSlaveBot;
    }

    private void instructSlaveToPlay(Bot slaveBot, MessageReceivedEvent event) {
        Guild guild = slaveBot.getJDA().getGuildById(event.getGuild().getId());
        if (guild == null) {
            event.getChannel().sendMessage("This slave bot is not in the same server.").queue();
            return;
        }

        // Ensure the AudioHandler is set up for the slave bot in the guild
        AudioHandler handler = slaveBot.getPlayerManager().createAudioHandler(guild);
        if (handler == null) {
            event.getChannel().sendMessage("Error: AudioHandler is not initialized.").queue();
            System.out.println("Error: AudioHandler is null.");
            return;
        }

        // Check if the user is in a voice channel, and connect the slave bot to it
        Member member = event.getMember();
        if (member != null && member.getVoiceState() != null && member.getVoiceState().inVoiceChannel()) {
            VoiceChannel voiceChannel = member.getVoiceState().getChannel();
            if (!guild.getAudioManager().isConnected()) {
                // Open audio connection for the slave bot
                guild.getAudioManager().openAudioConnection(voiceChannel);
                event.getChannel().sendMessage("Slave bot joining voice channel to play the track...").queue();
            }

            // Now log the slave bot selection to verify
            System.out.println("Selected Slave Bot: " + slaveBot.getJDA().getSelfUser().getName());

            // Call the SlavePlayCommand directly
            SlavePlayCommand slavePlayCommand = new SlavePlayCommand(slaveBot, guild, event.getTextChannel());
            slavePlayCommand.execute(event);  // Execute the play logic using the selected slave bot
        } else {
            event.getChannel().sendMessage("You need to be in a voice channel for the slave bot to play music!").queue();
        }
    }



}
