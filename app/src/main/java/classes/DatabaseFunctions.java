package classes;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.LinkedList;
import java.util.Random;

import database.RideTheBusContract;
import database.RideTheBusContract.CardTable;
import database.RideTheBusContract.GameTable;
import database.RideTheBusContract.PlayerStateTable;
import database.RideTheBusContract.PlayerDetailsTable;
import database.RideTheBusDbHelper;

public class DatabaseFunctions {

    private DatabaseFunctions() {}

    public static final String sharedPrefId = "myPrefs";

    // returns whether player must take drink or not
    public static boolean isTakeDrink(RideTheBusDbHelper dbHelper, long gameId, int choice) {
        Cursor game = getGameWhereId(dbHelper, gameId);
        game.moveToNext();
        long playerId = game.getLong(game.getColumnIndexOrThrow(GameTable.COLUMN_PLAYER_ID));
        game.close();
        Cursor cards = getCardWherePlayerId(dbHelper, gameId, playerId);
        boolean takeDrink = false;
        if (cards.getCount() == 1) {
            // red black
            cards.moveToNext();
            int cardValue = cards.getInt(cards.getColumnIndexOrThrow(CardTable.COLUMN_VALUE));
            takeDrink = choice == 0 ? !CardFunctions.isRed(cardValue) : !CardFunctions.isBlack(cardValue);
        } else if (cards.getCount() == 2) {
            // higher lower
            cards.moveToNext();
            int cardValue1 = cards.getInt(cards.getColumnIndexOrThrow(CardTable.COLUMN_VALUE));
            cards.moveToNext();
            int cardValue2 = cards.getInt(cards.getColumnIndexOrThrow(CardTable.COLUMN_VALUE));
            takeDrink = choice == 0 ? !CardFunctions.isHigher(cardValue1, cardValue2) : !CardFunctions.isLower(cardValue1, cardValue2);
        } else if (cards.getCount() == 3) {
            // in between outside
            cards.moveToNext();
            int cardValue1 = cards.getInt(cards.getColumnIndexOrThrow(CardTable.COLUMN_VALUE));
            cards.moveToNext();
            int cardValue2 = cards.getInt(cards.getColumnIndexOrThrow(CardTable.COLUMN_VALUE));
            cards.moveToNext();
            int cardValue3 = cards.getInt(cards.getColumnIndexOrThrow(CardTable.COLUMN_VALUE));
            takeDrink = choice == 0 ? !CardFunctions.isBetween(cardValue1, cardValue2, cardValue3) : !CardFunctions.isOutside(cardValue1, cardValue2, cardValue3);
        } else if (cards.getCount() == 4){
            // suit
            cards.moveToNext();
            cards.moveToNext();
            cards.moveToNext();
            cards.moveToNext();
            int cardValue = cards.getInt(cards.getColumnIndexOrThrow(CardTable.COLUMN_VALUE));
            switch (choice) {
                case 0:
                    takeDrink = !CardFunctions.isDiamond(cardValue);
                    break;
                case 1:
                    takeDrink = !CardFunctions.isHeart(cardValue);
                    break;
                case 2:
                    takeDrink = !CardFunctions.isClub(cardValue);
                    break;
                case 3:
                    takeDrink = !CardFunctions.isSpade(cardValue);
                    break;
            }
        }
        cards.close();
        return takeDrink;
    }

    public static void nextCard(RideTheBusDbHelper dbHelper, long gameId) {
        Cursor game = getGameWhereId(dbHelper, gameId);
        game.moveToNext();
        long playerId = game.getLong(game.getColumnIndexOrThrow(GameTable.COLUMN_PLAYER_ID));
        game.close();
        if (playerId == -1) {
            playerId = getPlayerStateIdWhereTurn(dbHelper, gameId, 0);
            setGamePlayerId(dbHelper, gameId, playerId);
        }
        Cursor cards = getCardWherePlayerId(dbHelper, gameId, playerId);
        if (cards.getCount() < 4) {
            long cardId = getEmptyCardId(dbHelper, gameId);
            setCardPlayerId(dbHelper, cardId, playerId, cards.getCount());
            setGameCardId(dbHelper, gameId, cardId);
        } else if (cards.getCount() == 4) {
            Cursor playerStates = getPlayerState(dbHelper, gameId);
            playerStates.moveToNext();
            while (playerStates.getLong(playerStates.getColumnIndexOrThrow(PlayerStateTable._ID)) != playerId) {
                playerStates.moveToNext();
            }
            if (playerStates.isLast()) {
                createDiamond(dbHelper, gameId);
            } else {
                playerStates.moveToNext();
                long nextPlayerId = playerStates.getLong(playerStates.getColumnIndexOrThrow(PlayerStateTable._ID));
                long cardId = getEmptyCardId(dbHelper, gameId);
                setCardPlayerId(dbHelper, cardId, nextPlayerId, 0);
                setGameCardId(dbHelper, gameId, cardId);
                setGamePlayerId(dbHelper, gameId, nextPlayerId);
            }
        }
        cards.close();
    }

    public static int flipStage2(RideTheBusDbHelper dbHelper, long gameId) {
        Cursor game = getGameWhereId(dbHelper, gameId);
        game.moveToNext();
        // long playerId = game.getLong(game.getColumnIndexOrThrow(GameTable.COLUMN_PLAYER_ID));
        long cardId = game.getLong(game.getColumnIndexOrThrow(GameTable.COLUMN_CARD_ID));
        game.close();
//        long playerId = getPlayerStateIdWhereTurn(dbHelper, gameId, 0);
//        setGamePlayerId(dbHelper, gameId, playerId);
        // nextPlayerStage2(dbHelper, gameId);
        return getCardValue(dbHelper, cardId);
    }

    public static long nextPlayerStage2(RideTheBusDbHelper dbHelper, long gameId) {
        Cursor game = getGameWhereId(dbHelper, gameId);
        game.moveToNext();
        long playerId = game.getLong(game.getColumnIndexOrThrow(GameTable.COLUMN_PLAYER_ID));
        long cardId = game.getLong(game.getColumnIndexOrThrow(GameTable.COLUMN_CARD_ID));
        game.close();
        LinkedList<Long> players = getPlayersWithCardValue(dbHelper, gameId, getCardValue(dbHelper, cardId));

        if (players.size() == 0) {
            nextDiamondCard(dbHelper, gameId);
            return -1;
        } else if (playerId == -1) {
            setGamePlayerId(dbHelper, gameId, players.get(0));
            return players.get(0);
        } else {
            int index = players.indexOf(playerId);
            if (index == players.size() - 1) {
                setGamePlayerId(dbHelper, gameId, -1);
                nextDiamondCard(dbHelper, gameId);
                return -1;
            } else {
                setGamePlayerId(dbHelper, gameId, players.get(index + 1));
                return players.get(index + 1);
            }
        }
    }

    public static boolean nextDiamondCard(RideTheBusDbHelper dbHelper, long gameId) {
        Cursor game = getGameWhereId(dbHelper, gameId);
        game.moveToNext();
        long cardId = game.getLong(game.getColumnIndexOrThrow(GameTable.COLUMN_CARD_ID));
        game.close();
        Cursor diamondCards = getDiamondCards(dbHelper, gameId);
        diamondCards.moveToNext();
        while (!diamondCards.isAfterLast() && cardId != diamondCards.getLong(diamondCards.getColumnIndexOrThrow(CardTable._ID))) {
            diamondCards.moveToNext();
        }
        if (diamondCards.isAfterLast() || diamondCards.isLast()) {
            return false;
        } else {
            diamondCards.moveToNext();
            long nextCardId = diamondCards.getLong(diamondCards.getColumnIndexOrThrow(CardTable._ID));
            setGameCardId(dbHelper, gameId, nextCardId);
            return true;
        }

    }

    public static long stage2CurrentPlayer(RideTheBusDbHelper dbHelper, long gameId) {
        Cursor game = getGameWhereId(dbHelper, gameId);
        game.moveToNext();
        return game.getLong(game.getColumnIndexOrThrow(GameTable.COLUMN_PLAYER_ID));
    }

//    public static long stage2OtherPlayers(RideTheBusDbHelper dbHelper, long gameId, long playerId) {
//
//    }

    public static void createDiamond(RideTheBusDbHelper dbHelper, long gameId) {
        for (int i = 0; i < 16; i++) {
            long cardId = getEmptyCardId(dbHelper, gameId);
            setCardDiamondOrder(dbHelper, cardId, i);
        }
        Cursor diamondCards = getDiamondCards(dbHelper, gameId);
        diamondCards.moveToNext();
        setGamePlayerId(dbHelper, gameId, -1);
        setGameCardId(dbHelper, gameId, diamondCards.getLong(diamondCards.getColumnIndexOrThrow(CardTable._ID)));
    }

    public static int getNumberOfPlayers(RideTheBusDbHelper dbHelper, long gameId) {
        return getPlayerState(dbHelper, gameId).getCount();
    }

    public static int getCurrentCard(RideTheBusDbHelper dbHelper, long gameId) {
        Cursor game = getGameWhereId(dbHelper, gameId);
        game.moveToNext();
        long cardId = game.getLong(game.getColumnIndexOrThrow(GameTable.COLUMN_CARD_ID));
        game.close();
        return getCardValue(dbHelper, cardId);
    }

    public static String getCurrentPlayerName(RideTheBusDbHelper dbHelper, long gameId) {
        Cursor game = getGameWhereId(dbHelper, gameId);
        game.moveToNext();
        long playerId = game.getLong(game.getColumnIndexOrThrow(GameTable.COLUMN_PLAYER_ID));
        long playerDetailsId = getPlayerStatePlayerId(dbHelper, gameId, playerId);
        return getPlayerDetailsName(dbHelper, playerDetailsId);
    }

    public static Cursor getGameWhereId(RideTheBusDbHelper dbHelper, long gameId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // SELECT
        String[] projection = {
                RideTheBusContract.GameTable._ID,
                RideTheBusContract.GameTable.COLUMN_CARD_ID,
                RideTheBusContract.GameTable.COLUMN_PLAYER_ID
        };
        // FROM
        String tableName = RideTheBusContract.GameTable.TABLE_NAME;
        // WHERE
        String selection = RideTheBusContract.GameTable._ID + " = ?";
        String[] selectionArgs = { Long.toString(gameId) };
        // SORT
        String sortOrder =
                RideTheBusContract.GameTable._ID + " DESC";
        // QUERY
        Cursor cursor = db.query(
                tableName,                                // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort turn
        );
        return cursor;
    }

    public static void deleteAllTables(RideTheBusDbHelper dbHelper) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(GameTable.TABLE_NAME, null, null);
        db.delete(CardTable.TABLE_NAME, null, null);
        db.delete(PlayerStateTable.TABLE_NAME, null, null);
        db.delete(PlayerDetailsTable.TABLE_NAME, null, null);
    }

    public static int getCardForPlayer(RideTheBusDbHelper dbHelper, long gameId, int playerIndex, int cardIndex) {
        Cursor game = getGameWhereId(dbHelper, gameId);
        game.moveToNext();
        long playerId = game.getLong(game.getColumnIndexOrThrow(GameTable.COLUMN_PLAYER_ID));
        Cursor allCards = getCardWherePlayerId(dbHelper, gameId, playerId);
        allCards.moveToNext();
        for (int i = 0; i < cardIndex; i++) {
            allCards.moveToNext();
        }
        return allCards.getInt(allCards.getColumnIndexOrThrow(CardTable.COLUMN_VALUE));
    }

    public static Cursor getCardWherePlayerId(RideTheBusDbHelper dbHelper, long gameId, long playerId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // SELECT
        String[] projection = {
                CardTable._ID,
                CardTable.COLUMN_VALUE,
                CardTable.COLUMN_GAME_ID,
                CardTable.COLUMN_DIAMOND_ORDER,
                CardTable.COLUMN_PLAYER_ID,
                CardTable.COLUMN_PLAYER_ORDER
        };
        // FROM
        String tableName = CardTable.TABLE_NAME;
        // WHERE
        String selection = CardTable.COLUMN_GAME_ID + " = ? AND " + CardTable.COLUMN_PLAYER_ID + " = ?";
        String[] selectionArgs = { Long.toString(gameId), Long.toString(playerId) };
        // SORT
        String sortOrder =
                CardTable.COLUMN_PLAYER_ORDER + " ASC";
        // QUERY
        Cursor cursor = db.query(
                tableName,                                // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort turn
        );
        return cursor;
    }

    public static LinkedList<Long> getPlayersWithCardValue(RideTheBusDbHelper dbHelper, long gameId, int cardVal) {
        Cursor players = getPlayerState(dbHelper, gameId);
        Cursor cards = getCardWithValue(dbHelper, gameId, cardVal);
        LinkedList<Long> playersWithCard = new LinkedList<Long>();
        LinkedList<Long> cardPlayerIds = new LinkedList<Long>();
        while (cards.moveToNext()) {
            long cardPlayerId = cards.getLong(cards.getColumnIndexOrThrow(CardTable.COLUMN_PLAYER_ID));
            if (cardPlayerId > 0 && !cardPlayerIds.contains(cardPlayerId)) {
                cardPlayerIds.add(cardPlayerId);
            }
        }

        while (players.moveToNext()) {
            long playerId = players.getLong(cards.getColumnIndexOrThrow(PlayerStateTable._ID));
            if (cardPlayerIds.contains(playerId)) {
                playersWithCard.add(playerId);
            }
        }
        return playersWithCard;
    }

    public static int getCardValue(RideTheBusDbHelper dbHelper, long cardId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // SELECT
        String[] projection = {
                CardTable.COLUMN_VALUE
        };
        // FROM
        String tableName = CardTable.TABLE_NAME;
        // WHERE
        String selection = CardTable._ID + " = ?";
        String[] selectionArgs = { Long.toString(cardId) };
        // SORT
        String sortOrder =
                CardTable._ID+ " DESC";
        // QUERY
        Cursor cursor = db.query(
                tableName,                                // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort turn
        );
        cursor.moveToNext();
        return cursor.getInt(cursor.getColumnIndexOrThrow(CardTable.COLUMN_VALUE));
    }

    public static Cursor getCardWithValue(RideTheBusDbHelper dbHelper, long gameId, int value) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // SELECT
        String[] projection = {
                CardTable._ID,
                CardTable.COLUMN_VALUE,
                CardTable.COLUMN_GAME_ID,
                CardTable.COLUMN_DIAMOND_ORDER,
                CardTable.COLUMN_PLAYER_ID,
                CardTable.COLUMN_PLAYER_ORDER
        };
        // FROM
        String tableName = CardTable.TABLE_NAME;
        // WHERE
        String selection = CardTable.COLUMN_GAME_ID + " = ? AND " + CardTable.COLUMN_VALUE + " = ?";
        String[] selectionArgs = { Long.toString(gameId), Integer.toString(value) };
        // SORT
        String sortOrder =
                CardTable._ID+ " ASC";
        // QUERY
        Cursor cursor = db.query(
                tableName,                                // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort turn
        );

        return cursor;
    }

    public static Long getEmptyCardId(RideTheBusDbHelper dbHelper, long gameId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // SELECT
        String[] projection = {
                CardTable._ID,
                CardTable.COLUMN_VALUE,
                CardTable.COLUMN_GAME_ID,
                CardTable.COLUMN_DIAMOND_ORDER,
                CardTable.COLUMN_PLAYER_ID,
                CardTable.COLUMN_PLAYER_ORDER
        };
        // FROM
        String tableName = CardTable.TABLE_NAME;
        // WHERE
        String selection = CardTable.COLUMN_GAME_ID + " = ? AND " + CardTable.COLUMN_PLAYER_ORDER + " = ? AND " + CardTable.COLUMN_DIAMOND_ORDER + " = ?";
        String[] selectionArgs = { Long.toString(gameId), Long.toString(-1), Long.toString(-1) };
        // SORT
        String sortOrder =
                CardTable.COLUMN_VALUE + " DESC";
        // QUERY
        Cursor cursor = db.query(
                tableName,                                // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort turn
        );


        Random rand = new Random();
        int index = rand.nextInt(cursor.getCount() - 1);
        cursor.moveToNext();
        for (int i = 0; i < index; i++) {
            cursor.moveToNext();
        }

        return cursor.getLong(cursor.getColumnIndexOrThrow(CardTable._ID));
    }

    public static boolean isDiamond(RideTheBusDbHelper dbHelper, long gameId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // SELECT
        String[] projection = {
                CardTable._ID,
                CardTable.COLUMN_VALUE,
                CardTable.COLUMN_GAME_ID,
                CardTable.COLUMN_DIAMOND_ORDER,
                CardTable.COLUMN_PLAYER_ID,
                CardTable.COLUMN_PLAYER_ORDER
        };
        // FROM
        String tableName = CardTable.TABLE_NAME;
        // WHERE
        String selection = CardTable.COLUMN_GAME_ID + " = ? AND " + CardTable.COLUMN_DIAMOND_ORDER + " = ?";
        String[] selectionArgs = { Long.toString(gameId), Long.toString(0) };
        // SORT
        String sortOrder =
                CardTable.COLUMN_VALUE + " DESC";
        // QUERY
        Cursor cursor = db.query(
                tableName,                                // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort turn
        );

        boolean ret = cursor.getCount() > 0;
        cursor.close();

        return ret;
    }

    public static Cursor getDiamondCards(RideTheBusDbHelper dbHelper, long gameId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // SELECT
        String[] projection = {
                CardTable._ID,
                CardTable.COLUMN_VALUE,
                CardTable.COLUMN_GAME_ID,
                CardTable.COLUMN_DIAMOND_ORDER,
                CardTable.COLUMN_PLAYER_ID,
                CardTable.COLUMN_PLAYER_ORDER
        };
        // FROM
        String tableName = CardTable.TABLE_NAME;
        // WHERE
        String selection = CardTable.COLUMN_GAME_ID + " = ? AND " + CardTable.COLUMN_DIAMOND_ORDER + " > ?";
        String[] selectionArgs = { Long.toString(gameId), Integer.toString(-1) };
        // SORT
        String sortOrder =
                CardTable.COLUMN_DIAMOND_ORDER + " ASC";
        // QUERY
        Cursor cursor = db.query(
                tableName,                                // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort turn
        );

        return cursor;
    }

    public static int getCardAtDiamondPos(RideTheBusDbHelper dbHelper, long gameId, int pos) {
        Cursor diamondCards = getDiamondCards(dbHelper, gameId);
        diamondCards.moveToNext();
        for(int i = 0; i < pos; i++) {
            diamondCards.moveToNext();
        }
        long cardId = diamondCards.getLong(diamondCards.getColumnIndexOrThrow(CardTable._ID));
        return getCardValue(dbHelper, cardId);
    }

    public static Cursor getPlayerState(RideTheBusDbHelper dbHelper, long gameId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // SELECT
        String[] projection = {
                PlayerStateTable.COLUMN_PLAYER_ID,
                PlayerStateTable.COLUMN_DRINKS_TAKEN,
                PlayerStateTable.COLUMN_MAX_DRINKS,
                PlayerStateTable.COLUMN_TURN,
                PlayerStateTable.COLUMN_GAME_ID,
                PlayerStateTable._ID,
        };
        // FROM
        String tableName = PlayerStateTable.TABLE_NAME;
        // WHERE
        String selection = PlayerStateTable.COLUMN_GAME_ID + " = ?";
        String[] selectionArgs = { Long.toString(gameId) };
        // SORT
        String sortOrder =
                PlayerStateTable.COLUMN_TURN+ " ASC";
        // QUERY
        Cursor cursor = db.query(
                tableName,                                // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort turn
        );

        return cursor;

    }

    public static Long getPlayerStatePlayerId(RideTheBusDbHelper dbHelper, long gameId, long playerStateId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // SELECT
        String[] projection = {
                PlayerStateTable.COLUMN_PLAYER_ID
        };
        // FROM
        String tableName = PlayerStateTable.TABLE_NAME;
        // WHERE
        String selection = PlayerStateTable._ID + " = ?";
        String[] selectionArgs = { Long.toString(playerStateId) };
        // SORT
        String sortOrder =
                PlayerStateTable._ID+ " DESC";
        // QUERY
        Cursor cursor = db.query(
                tableName,                                // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort turn
        );

        cursor.moveToNext();
        return cursor.getLong(cursor.getColumnIndexOrThrow(PlayerStateTable.COLUMN_PLAYER_ID));
    }

    public static String getPlayerDetailsName(RideTheBusDbHelper dbHelper, long playerDetailsId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // SELECT
        String[] projection = {
                PlayerDetailsTable.COLUMN_NAME
        };
        // FROM
        String tableName = PlayerDetailsTable.TABLE_NAME;
        // WHERE
        String selection = PlayerDetailsTable._ID + " = ?";
        String[] selectionArgs = { Long.toString(playerDetailsId) };
        // SORT
        String sortOrder =
                PlayerDetailsTable._ID+ " DESC";
        // QUERY
        Cursor cursor = db.query(
                tableName,                                // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort turn
        );

        cursor.moveToNext();
        return cursor.getString(cursor.getColumnIndexOrThrow(PlayerDetailsTable.COLUMN_NAME));
    }

    public static long getPlayerStateIdWhereTurn(RideTheBusDbHelper dbHelper, long gameId, int turn) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // SELECT
        String[] projection = {
                PlayerStateTable._ID
        };
        // FROM
        String tableName = PlayerStateTable.TABLE_NAME;
        // WHERE
        String selection = PlayerStateTable.COLUMN_TURN + " = ?";
        String[] selectionArgs = { Integer.toString(turn) };
        // SORT
        String sortOrder =
                PlayerStateTable._ID+ " DESC";
        // QUERY
        Cursor cursor = db.query(
                tableName,                                // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort turn
        );

        cursor.moveToNext();
        return cursor.getLong(cursor.getColumnIndexOrThrow(PlayerStateTable._ID));
    }

    public static void setCardPlayerId(RideTheBusDbHelper dbHelper, long cardId, long playerId, int playerOrder) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // New value for one column
        ContentValues values = new ContentValues();
        values.put(CardTable.COLUMN_PLAYER_ID, playerId);
        values.put(CardTable.COLUMN_PLAYER_ORDER, playerOrder);

        // Which row to update, based on the title
        String selection = CardTable._ID + " LIKE ?";
        String[] selectionArgs = { Long.toString(cardId) };

        int count = db.update(
                CardTable.TABLE_NAME,
                values,
                selection,
                selectionArgs);
    }

    public static void setCardDiamondOrder(RideTheBusDbHelper dbHelper, long cardId, int diamondOrder) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // New value for one column
        ContentValues values = new ContentValues();
        values.put(CardTable.COLUMN_DIAMOND_ORDER, diamondOrder);

        // Which row to update, based on the title
        String selection = CardTable._ID + " LIKE ?";
        String[] selectionArgs = { Long.toString(cardId) };

        int count = db.update(
                CardTable.TABLE_NAME,
                values,
                selection,
                selectionArgs);
    }

    public static void setGameCardId(RideTheBusDbHelper dbHelper, long gameId, long cardId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // New value for one column
        ContentValues values = new ContentValues();
        values.put(GameTable.COLUMN_CARD_ID, cardId);

        // Which row to update, based on the title
        String selection = GameTable._ID + " LIKE ?";
        String[] selectionArgs = { Long.toString(gameId) };

        int count = db.update(
                GameTable.TABLE_NAME,
                values,
                selection,
                selectionArgs);
    }

    public static void setGamePlayerId(RideTheBusDbHelper dbHelper, long gameId, long playerId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // New value for one column
        ContentValues values = new ContentValues();
        values.put(GameTable.COLUMN_PLAYER_ID, playerId);

        // Which row to update, based on the title
        String selection = GameTable._ID + " LIKE ?";
        String[] selectionArgs = { Long.toString(gameId) };

        int count = db.update(
                GameTable.TABLE_NAME,
                values,
                selection,
                selectionArgs);
    }
}
