package com.jagrosh.jmusicbot;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MasterListener extends ListenerAdapter {
    private final Bot bot;

    public MasterListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return; // Ignore bot messages

        String message = event.getMessage().getContentRaw();
        if (message.startsWith("!play")) {
            // Master bot receives the !play command
            event.getChannel().sendMessage("Master bot received the play command. Selecting a slave to handle it...").queue();

            // Select a slave bot to handle the command
            Bot selectedSlaveBot = selectSlaveBot();
            if (selectedSlaveBot != null) {
                // Forward the command to the slave bot to execute
                instructSlaveToPlay(selectedSlaveBot, event);
            } else {
                event.getChannel().sendMessage("No available slave bots to handle the command.").queue();
            }
        }
    }

    private Bot selectSlaveBot() {
        // Logic to select a slave bot (e.g., random or round-robin)
        return null;  // Implement your slave selection logic here
    }

    private void instructSlaveToPlay(Bot slaveBot, MessageReceivedEvent event) {
        // Instruct the selected slave bot to handle the !play command
        String command = event.getMessage().getContentRaw(); // Get the actual !play command
        event.getChannel().sendMessage("Instructing the slave bot to play the song...").queue();
        // Send the command to the slave bot to handle
        // This could be done by invoking the play command on the slave bot's listener
        // Example: you might send a message or call a method directly
    }
}
