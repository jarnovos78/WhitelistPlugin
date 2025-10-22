package com.example.whitelistplugin;

import org.jetbrains.annotations.NotNull;
import spark.Response;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DataBaseManager {

    private Connection connection;

    public void connect() throws SQLException {
        try {
            // Plugin-Ordner erstellen falls nicht vorhanden
            File pluginFolder = new File("plugins/WhitelistPlugin");
            if (!pluginFolder.exists()) {
                pluginFolder.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        connection = DriverManager.getConnection("jdbc:sqlite:plugins/WhitelistPlugin/coords.db");
        createDataBase();
    }

    private void createDataBase() throws SQLException {

        String sql = "CREATE TABLE IF NOT EXISTS coords (" +
                "uuid TEXT NOT NULL, " +
                "x INTEGER, " +
                "y INTEGER, " +
                "z INTEGER, " +
                "Description TEXT, " +
                "Player TEXT, " +
                "accessPublic BOOLEAN, " +
                "PRIMARY KEY (uuid))";

        Statement statement = connection.createStatement();
        statement.execute(sql);
        statement.close();
    }

    public List<LocationEntity> loadCords() throws SQLException {
        String sql = "SELECT Description, x, y, z, Player FROM coords WHERE accessPublic = true";
        PreparedStatement statement = connection.prepareStatement(sql);
        return getLocationEntities(statement);
    }

    public List<LocationEntity> loadPlayerSpecificCords(String playerName) throws SQLException {
        String sql = "SELECT Description, x, y, z, Player FROM coords WHERE Player = ?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, playerName);
        return getLocationEntities(statement);
    }

    @NotNull
    private List<LocationEntity> getLocationEntities(PreparedStatement statement) throws SQLException {
        ResultSet resultSet = statement.executeQuery();

        List<LocationEntity> cordList = new ArrayList<>();
        while (resultSet.next()) {
            LocationEntity locationEntity = new LocationEntity();
            locationEntity.setDescription(resultSet.getString("Description"));
            locationEntity.setX(resultSet.getInt("x"));
            locationEntity.setY(resultSet.getInt("y"));
            locationEntity.setZ(resultSet.getInt("z"));
            locationEntity.setPlayerName(resultSet.getString("Player"));
            cordList.add(locationEntity);
        }
        resultSet.close();
        statement.close();

        return cordList;
    }

    public boolean checkExists(String description, boolean accessPublic, String player) throws SQLException {
        String sql;
        if(accessPublic) {
            sql = "SELECT Description FROM coords WHERE Description = ? AND accessPublic = true";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, description);
            ResultSet result = statement.executeQuery();
            if(result.next()) {
                statement.close();
                return true;
            }
            statement.close();
        } else {
            sql = "SELECT Description FROM coords WHERE Player = ? AND Description = ? AND accessPublic = false";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, player);
            statement.setString(2, description);
            ResultSet result = statement.executeQuery();
            if(result.next()) {
                statement.close();
                return true;
            }
            statement.close();
        }

        return false;
    }

    public void safeCords(int x, int y, int z, String description, String player, boolean accessPublic) throws SQLException {
        UUID uuid = UUID.randomUUID();
        String sql = "INSERT INTO coords (uuid, x, y, z, Description, Player, accessPublic) VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement statement = connection.prepareStatement(sql);

        statement.setString(1, uuid.toString());
        statement.setInt(2, x);
        statement.setInt(3, y);
        statement.setInt(4, z);
        statement.setString(5, description);
        statement.setString(6, player);
        statement.setBoolean(7, accessPublic);
        statement.executeUpdate();
        statement.close();
    }

    public void closeDataBase() throws SQLException {
        if(connection != null) connection.close();
    }

}
