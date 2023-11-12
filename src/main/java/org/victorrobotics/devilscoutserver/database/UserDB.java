package org.victorrobotics.devilscoutserver.database;

public interface UserDB {
  User getUser(int team, String username);

  default byte[] getSalt(int team, String username) {
    User entry = getUser(team, username);
    return entry == null ? null : entry.salt();
  }

  void putNonce(String nonceID);

  boolean containsNonce(String nonceID);

  void removeNonce(String nonceID);
}
