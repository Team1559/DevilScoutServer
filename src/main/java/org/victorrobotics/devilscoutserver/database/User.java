package org.victorrobotics.devilscoutserver.database;

import static org.victorrobotics.devilscoutserver.EncodingUtil.base64Decode;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.annotation.JsonIgnore;

@SuppressWarnings("java:S6218") // arrays in equals
public record User(String id,
                   int team,
                   String username,
                   String fullName,
                   boolean admin,
                   @JsonIgnore byte[] salt,
                   @JsonIgnore byte[] storedKey,
                   @JsonIgnore byte[] serverKey) {
  public static User fromDatabase(ResultSet resultSet) throws SQLException {
    String id = resultSet.getString(1);
    int team = resultSet.getInt(2);
    String username = resultSet.getString(3);
    String fullName = resultSet.getString(4);
    boolean admin = resultSet.getBoolean(5);
    byte[] salt = base64Decode(resultSet.getString(6));
    byte[] storedKey = base64Decode(resultSet.getString(7));
    byte[] serverKey = base64Decode(resultSet.getString(8));
    return new User(id, team, username, fullName, admin, salt, storedKey, serverKey);
  }
}
