package com.example.whitelistplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import spark.Spark;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class WhitelistPlugin extends JavaPlugin {

    private final String API_KEY = "T3Fees290DevW2";

    private final String WebhookURL = "https://discord.com/api/webhooks/1422160876227526656/mbqwQrJCCUeCmzLCs8LPLNsMn4k95u-wm3k55Lz--cuY8Qb-jFqW8WD4ncEGvc6LDly3";

    @Override
    public void onEnable(){
        getLogger().info("Whitelist Plugin Enabled");

        Spark.port(4567);

        Spark.post("/whitelist" , (request, response) -> {
            String apiKey = request.queryParams("key");
            String playerName = request.queryParams("playerName");

            if(apiKey == null || playerName == null || apiKey.isEmpty() || playerName.isEmpty()) {
                response.status(400);
                return "ungültige API-Anfrage";
            }

            if(!apiKey.equals(API_KEY)){
                response.status(403);
                return "ungültiger API-Key";
            }

            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + playerName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
            });

            response.status(200);
            return "Spieler " + playerName + " wurde der whitelist hinzugefügt";
        });
    }

    @Override
    public void onDisable(){
        Spark.stop();
        getLogger().info("Spark server wurde gestoppt");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("safeCords")) {
            if (args.length == 0) {
                sender.sendMessage("Bitte gib einen Namen für den Ort an!");
                return false; // zeigt die Usage aus plugin.yml
            }

            if(!(sender instanceof Player player)){
                sender.sendMessage("Dieser Befehl kann nur von einem Spieler ausgeführt werden!");
                return false;
            }
            Location playerLocation = player.getLocation();
            double x = playerLocation.getX();
            double y = playerLocation.getY();
            double z = playerLocation.getZ();
            String coordinates = "(" + x + ", " + y + ", " + z + ")";
            String coordinateDescription = String.join(" ", args);

            String safedLocation = coordinateDescription + ", " + coordinates;

            //Nachricht für die Konsole
            getLogger().info(sender.getName() + " hat folgende Koordinaten Gespeichert: " + safedLocation);

            int responseCode = sendWebhookDiscordMessage(safedLocation);

            if(responseCode != 200){
                sender.sendMessage("Fehler beim speichern der Koordinaten. Bitte versuche es erneut.");
                return false;
            }

            sender.sendMessage("Location " + coordinateDescription + " wurde mit den folgenden Koordinaten gespeichert: " + coordinates);
            return true;
        }
        return false;
    }

    public int sendWebhookDiscordMessage(String safedLocation){
        try {
            URL url = new URL(WebhookURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            String jsonPayload = "{\"content\": \"" + safedLocation + "\"}";

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes());
                os.flush();
            }

            return connection.getResponseCode();

        } catch (Exception e){
            e.printStackTrace();
        }
        return 0;
    }

}
