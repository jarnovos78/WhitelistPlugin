package com.example.whitelistplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WhitelistPlugin extends JavaPlugin implements TabCompleter {

    private final String API_KEY = "T3Fees290DevW2";

    private final String WebhookURL = "https://discord.com/api/webhooks/1428420414178332833/M-3x-Gb2wRpuV9koY12-xjWM1jJp4zW4ntNCpNZHhI210SliL6QAsX41NzRA5TqJi9ou";

    private DataBaseManager dataBaseManager;

    @Override
    public void onEnable(){
        getLogger().info("Whitelist Plugin Enabled");
        Spark.port(4567);
        Spark.post("/whitelist" , this::whitelistPlayer);

        dataBaseManager = new DataBaseManager();

        try {
            dataBaseManager.connect();
            getLogger().info("SQLite verbunden!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable(){
        Spark.stop();
        getLogger().info("Spark server wurde gestoppt");
        try {
            dataBaseManager.closeDataBase();
            getLogger().info("SQLite wurde gestoppt");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("safeCords") && args[0].equalsIgnoreCase("public")) {
            return safeCords(sender, args, true);
        }
        if(command.getName().equalsIgnoreCase("safeCords") && args[0].equalsIgnoreCase("private")) {
            return safeCords(sender, args, false);
        }
        if(command.getName().equalsIgnoreCase("showCords")) {
            return showCords(sender, args);
        }
        if(command.getName().equalsIgnoreCase("deletePrivateCords")) {
            return deletePrivateCords(sender, args);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("safeCords")) {
            if(args.length == 1) {
                completions.add("public");
                completions.add("private");
            }
            if (args.length == 2) {
                completions.add("<Name des Ortes>");
            }
        } else if (command.getName().equalsIgnoreCase("safePrivateCords")) {
            if (args.length == 1) {
                completions.add("<Name des Ortes>");
            }
        } else if(command.getName().equalsIgnoreCase("showCords")) {
            if (args.length == 1) {
                completions.add("public");
                completions.add("private");
            }
        } else if (command.getName().equalsIgnoreCase("deletePrivateCords")) {
            if (args.length == 1) {
                completions.add("<Name des Ortes>");
            }
        }
        return completions;
    }

    private String whitelistPlayer(Request request, Response response){
        String apiKey = request.queryParams("key");
        String playerName = request.queryParams("player");

        if(apiKey == null || playerName == null || apiKey.isEmpty() || playerName.isEmpty()) {
            response.status(400);
            System.out.println("apiKey: " + apiKey + ", playerName: " + playerName);
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
    }

    private boolean safeCords(CommandSender sender, String[] args, boolean accessPublic) {
        if (args.length == 1) {
            sender.sendMessage("Bitte gib einen Namen für den Ort an!");
            return false; // zeigt die Usage aus plugin.yml
        }

        if(!(sender instanceof Player player)){
            sender.sendMessage("Dieser Befehl kann nur von einem Spieler ausgeführt werden!");
            return false;
        }
        String playerName = player.getName();

        Location playerLocation = player.getLocation();
        int x = (int) playerLocation.getX();
        int y = (int) playerLocation.getY();
        int z = (int) playerLocation.getZ();
        String coordinates = "(" + x + ", " + y + ", " + z + ")";
        String coordinateDescription = String.join(" ", args[1]);

        String safedLocation = coordinateDescription + " " + coordinates + " | " + playerName;

        try {
            boolean alreadyExists = dataBaseManager.checkExists(coordinateDescription, accessPublic, playerName);
            if(alreadyExists) {
                sender.sendMessage("Fehler: Dieser Ort wurde schoneinmal gespeichert!");
                return true;
            }
        }catch(Exception e) {
            getLogger().warning("Fehler beim aufrufen der Datenbank beim Speichern von Koordinaten: " + safedLocation);
            getLogger().warning("Error: " + e);
        }

        if(accessPublic) {
            int responseCode = sendWebhookDiscordMessage(safedLocation);

            if (responseCode != 204) {
                sender.sendMessage("Fehler beim speichern der Koordinaten. Bitte versuche es erneut." + responseCode);
                return false;
            }
        }

        try {
            dataBaseManager.safeCords(x, y, z, coordinateDescription, playerName, accessPublic);
        }catch (Exception e){
            getLogger().warning("Fehler beim aufrufen der Datenbank beim Speichern von Koordinaten: " + safedLocation);
            getLogger().warning("Error: " + e);
        }

        sender.sendMessage("Location " + coordinateDescription + " wurde mit den folgenden Koordinaten gespeichert: " + coordinates);
        //Nachricht für die Konsole
        getLogger().info(playerName + " hat folgende Koordinaten Gespeichert: " + safedLocation);

        return true;
    }

    private boolean showCords(CommandSender sender, String[] args){
        String description = String.join(" ", args);
        if(description.equalsIgnoreCase("public")) {
            return showPublicCords(sender);
        } else if (description.equalsIgnoreCase("private")) {
            return showPrivateCords(sender);
        }
        return false;
    }

    private boolean showPublicCords(CommandSender sender) {
        try {
            List<LocationEntity> cordList = dataBaseManager.loadCords();
            if(cordList.isEmpty()) {
                sender.sendMessage("Es wurden noch keine Cords gespeichert!");
            }
            for (LocationEntity location : cordList) {
                String message = location.getDescription() + " (" + location.getX() + ", " + location.getY() + ", " + location.getZ() + ")";
                sender.sendMessage(message);
            }
            return true;

        } catch (Exception e) {
            sender.sendMessage("Es ist ein Fehler beim Laden der Koordinaten aufgetreten. Bitte versuche es erneut.");
            e.printStackTrace();
            return false;
        }
    }

    private boolean showPrivateCords(CommandSender sender){
        try {
            List<LocationEntity> cordList = dataBaseManager.loadPlayerSpecificCords(sender.getName());
            if(cordList.isEmpty()) {
                sender.sendMessage("Du hast noch keine privaten Cords gespeichert!");
                return true;
            }
            for(LocationEntity location : cordList){
                String message = location.getDescription() + " (" + location.getX() + ", " + location.getY() + ", " + location.getZ() + ")";
                sender.sendMessage(message);
            }
            return true;
        } catch (Exception e){
            sender.sendMessage("Es ist ein Fehler beim Laden der Koordinaten aufgetreten. Bitte versuche es erneut.");
            e.printStackTrace();
            return false;
        }
    }

    public boolean deletePrivateCords(CommandSender sender, String[] args){
        if (args.length == 0) {
            sender.sendMessage("Bitte gib einen Namen für den Ort an!");
            return false;
        }

        if(!(sender instanceof Player player)){
            sender.sendMessage("Dieser Befehl kann nur von einem Spieler ausgeführt werden!");
            return false;
        }
        String playerName = player.getName();
        String description = String.join(" ", args);

        try{
            int deletet = dataBaseManager.deletePrivateCords(playerName, description);
            if(deletet == 1){
                sender.sendMessage("Die Koordinaten mit Namen: " + description  + " wurden erfolgreich gelöscht!");
            } else {
                sender.sendMessage("Koordinaten mit Namen: " + description + " existieren nicht!");
            }

        } catch (SQLException e) {
            getLogger().warning("Fehler beim aufrufen der Datenbank beim Löschen von Koordinaten: " + description + " durch Spieler: " + playerName);
            getLogger().warning("Error: " + e);
        }
        return true;
    }


    private int sendWebhookDiscordMessage(String safedLocation){
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
