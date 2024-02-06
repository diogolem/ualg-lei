package aed.tables;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/*  @author Diogo Almeida
    Implemented for Data Structures and Algorithms, 3rd Semester, LEI, FCT, UAlg
 */


   /** Class KeyValueEntry
    - Used to simplify the storage of data related with keys and values */
class KeyValueEntry<Key,Value>
{
    Key Key;
    Value Value;
    int hashcode;

    int swaps;
    int maxSwaps;
    int recordsIndex;

    LocalDateTime timeStamp;

    KeyValueEntry(Key key, Value value){
        this.Key = key;
        this.Value = value;
        this.hashcode = (key.hashCode()  & 0x7fffffff);

        this.swaps = -1;
        this.maxSwaps = -1;

        this.timeStamp = LocalDateTime.now();
    }
}

public class ForgettingCuckooHashTable<Key,Value> implements ISymbolTable<Key,Value> {

    private static final int[] primesTable0 = {
            7, 17, 37, 79, 163, 331,
            673, 1361, 2729, 5471, 10949,
            21911, 43853, 87719, 175447, 350899,
            701819, 1403641, 2807303, 5614657,
            11229331, 22458671, 44917381, 89834777, 179669557
    };

    private static final int[] primesTable1 = {
            11, 19, 41, 83, 167, 337,
            677, 1367, 2731, 5477, 10957,
            21929, 43867, 87721, 175453, 350941,
            701837, 1403651, 2807323, 5614673,
            11229341, 22458677, 44917399, 89834821, 179669563
    };

    private int capacityTable0;
    private int capacityTable1;
    private int size;
    private int capacityIndex;

    private KeyValueEntry<Key, Value> [] table0;
    private KeyValueEntry<Key, Value> [] table1;
    private KeyValueEntry<Key, Value> [] keySwapRecords; // could've used a queue

    private boolean keySwapLogging;
    private int keySwapIndex;

    private final int maxSwaps;

    private LocalDateTime currentTime;

    // constructors
    @SuppressWarnings("unchecked")
    public ForgettingCuckooHashTable(int primeIndex) {
        if (primeIndex < 0 || primeIndex >= primesTable0.length) throw new IllegalArgumentException();

        // Capacity, Size and Tables initialization
        this.capacityTable0 = primesTable0[primeIndex];
        this.capacityTable1 = primesTable1[primeIndex];
        this.capacityIndex = primeIndex;
        this.size = 0;

        // table 0 and 1 of type KeyValueEntry initialization
        table0 = (KeyValueEntry<Key, Value> []) new KeyValueEntry[capacityTable0];
        table1 = (KeyValueEntry<Key, Value> []) new KeyValueEntry[capacityTable1];

        // Swap Logic initialization
        keySwapRecords = (KeyValueEntry<Key, Value> []) new KeyValueEntry[100];
        this.keySwapLogging = false;
        this.keySwapIndex = 0;
        this.maxSwaps = 15;

        // Time tings ;D
        this.currentTime = LocalDateTime.now();
    }

    public ForgettingCuckooHashTable() {
        this(0);
    }

    // Hashing functions
    private int h0(Key key) {
        return (key.hashCode() & 0x7fffffff) % capacityTable0;
    }

    private int h1(Key key) {
        return (~key.hashCode() & 0x7fffffff) % capacityTable1;
    }

    // return number of keys stored
    public int size() {
        return size;
    }

    // return true if table is empty
    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    // return total capacity of the table
    public int getCapacity() {
        return capacityTable0 + capacityTable1;
    }

    // relation between number of keys and table max capacity
    public float getLoadFactor() {
        return (float) size / getCapacity();
    }

    // return true if key exists on the table
    public boolean containsKey(Key k) {
        boolean containsKey = false;

        int hash0 = h0(k);
        int hash1 = h1(k);

        if (isEntryMatchingKey(table0[hash0], k)) {
            refreshTimeStamp(table0[hash0]); // updates interest on the key
            containsKey = true;
        }
        if (isEntryMatchingKey(table1[hash1], k)) {
            refreshTimeStamp(table1[hash1]);
            containsKey = true;
        }
        return containsKey;
    }

    // Returns the value associated with a certain key
    public Value get(Key k) {
        KeyValueEntry<Key, Value> entry0 = table0[h0(k)];

        if (isEntryMatchingKey(entry0, k)) {
            refreshTimeStamp(entry0);
            return entry0.Value;
        } else {
            KeyValueEntry<Key, Value> entry1 = table1[h1(k)];
            if (isEntryMatchingKey(entry1, k)) {
                refreshTimeStamp(entry1);
                return entry1.Value;
            }
        }
        return null;
    }

    // remove key-value associated with the given key k
    public void delete(Key k) {
        deleteFromTable(table0, h0(k), k);
        deleteFromTable(table1, h1(k), k);

        if (getLoadFactor() < 0.125f) {resizeTable(false);}
    }

    // HELPER METHOD: to know if the key given is the same as the one in the table at the same hash
    private boolean isEntryMatchingKey(KeyValueEntry<Key, Value> entry, Key k) {
        return entry != null && entry.Key.equals(k);
    }

    // HELPER METHOD: handles deleting from the table logic
    private void deleteFromTable(KeyValueEntry<Key, Value>[] table, int hash, Key k) {
        KeyValueEntry<Key, Value> entry = table[hash];
        if (isEntryMatchingKey(entry, k)) {
            table[hash] = null;
            size--;
        }
    }


    // puts Key-Value in the hashtable if the Key doesn't exist yet, If it does, it updates the value
    public void put(Key k, Value v) {
        if (k == null) throw new IllegalArgumentException();
        if (v == null) {
            delete(k);
            return;
        }
        if (getLoadFactor() > 0.5f) resizeTable(true); // increases table size if load factor > 0.5

        int hash0 = h0(k);
        int hash1 = h1(k);

        if (containsKey(k)) { // updates value associated with the key
            update(k,v,hash0,hash1);
            return;
        }

        KeyValueEntry<Key, Value> entry = new KeyValueEntry<>(k,v);

        if (threeWayCollision(entry, table0[hash0], table1[hash1])) throw new IllegalArgumentException("Three Keys with the same hashcode");

        while (true) { // Cuckoo Hashing
            if (maxSwapsReached(entry)) resizeTable(true); // If a key max swaps is reached, resize the table and try inserting again
            swapLogic(entry);

            if (table0[hash0] == null) { // Insert in table 0 and the position is free
                table0[hash0] = entry;
                size++;
                return;
            } else {
                if (shouldReplaceForgottenEntry(table0[hash0])) { // If the key in table 0 on the h0 position should be forgotten, place the new key over it.
                    forgottenSwapLogic(entry, table0[hash0]);
                    table0[hash0] = entry;
                    return;
                } // Insertion in T0 and it's occupied, the key at T0 now is the "entry" and it goes through the while
                KeyValueEntry<Key, Value> temp; //t0 e ocupado
                temp = table0[hash0];
                table0[hash0] = entry;
                entry = temp;
                hash1 = h1(entry.Key);
                swapLogic(entry);
            }
            if ((table1[hash1] == null)) { // Insertion in table 1 if table 0 failed or resulted in a swap
                table1[hash1] = entry;
                size++;
                return;
            } else if (shouldReplaceForgottenEntry(table1[hash1])) { // If the key at table 1 should be forgotten, place new key over it
                forgottenSwapLogic(entry, table1[hash1]);
                table1[hash1] = entry;
                return;
            } // Inserts in table 1 and the key that was previously there becomes the "entry" and the while iterates again
            KeyValueEntry<Key, Value> temp;
            temp = table1[hash1];
            table1[hash1] = entry;
            entry = temp;
            hash0 = h0(entry.Key);
        }
    }

    // HELPER METHOD: updates Key
    private void update(Key k, Value v, int hash0, int hash1){

        if (isEntryMatchingKey(table0[hash0], k)) {
            refreshTimeStamp(table0[hash0]);
            table0[hash0].Value = v;
        } else if (isEntryMatchingKey(table1[hash1], k)) {
            refreshTimeStamp(table1[hash1]);
            table1[hash1].Value = v;
        }
    }

    // HELPER METHOD: returns true if there's a collision between the *hashcode* of three different keys
    private boolean threeWayCollision(KeyValueEntry<Key, Value> entry, KeyValueEntry<Key, Value> entry0, KeyValueEntry<Key, Value> entry1) {
        return entry0 != null && entry1 != null && entry.hashcode == entry0.hashcode && entry.hashcode == entry1.hashcode;
    }

    // HELPER METHOD: returns true in case the maxSwaps of a certain key has gone over the maxSwaps defined
    private boolean maxSwapsReached(KeyValueEntry<Key, Value> entry) {
        return entry.maxSwaps > maxSwaps;
    }

    // HELPER METHOD: if the time between the forgotten entry time stamp and the currentTime > 24, returns true
    private boolean shouldReplaceForgottenEntry(KeyValueEntry<Key, Value> entry) {
        return !(differenceOfTimes(entry.timeStamp) < 24);
    }

    // put - HELPER METHOD: Resizes table and reinserts by rehashing all key-value pairs.
    public void resizeTable(boolean isIncreasing) {
        if (isIncreasing) capacityIndex++; // Increase table size
        if (!isIncreasing && capacityIndex > 0) capacityIndex--; // Decreases table size

        int newCapacityTable0 = primesTable0[capacityIndex];
        int newCapacityTable1 = primesTable1[capacityIndex];

        @SuppressWarnings("unchecked")
        KeyValueEntry<Key, Value> [] newTable0 = (KeyValueEntry<Key, Value> []) new KeyValueEntry[newCapacityTable0];
        @SuppressWarnings("unchecked")
        KeyValueEntry<Key, Value> [] newTable1 = (KeyValueEntry<Key, Value> []) new KeyValueEntry[newCapacityTable1];

        List<KeyValueEntry <Key, Value>> collisionsList = new ArrayList<>();

        for (KeyValueEntry<Key, Value> entry: table0) {
            if (entry != null) {
                int newH0 = (entry.hashcode & 0x7fffffff) % newCapacityTable0;
                addEntryToTable(newTable0, entry, newH0, collisionsList);
            }
        }

        for (KeyValueEntry<Key, Value> entry: table1) {
            if (entry != null) {
                int newH1 = (~entry.hashcode & 0x7fffffff) % newCapacityTable1;
                addEntryToTable(newTable1, entry, newH1, collisionsList);
            }
        }

        capacityTable0 = newCapacityTable0;
        capacityTable1 = newCapacityTable1;
        table0 = newTable0;
        table1 = newTable1;

        for (KeyValueEntry<Key,Value> entry : collisionsList){
            rePut(entry);
        }
        size -= collisionsList.size();
    }

    // Helper method to reput entries without counting for swaps and changes in timestamps
    private void rePut(KeyValueEntry<Key,Value> entry)
    {
        int hash0 = h0(entry.Key), hash1;

        while (true) {

            if (table0[hash0] == null) {
                table0[hash0] = entry;
                size++;
                return;
            } else {
                KeyValueEntry<Key, Value> temp;
                temp = table0[hash0];
                table0[hash0] = entry;
                entry = temp;
                hash1 = h1(entry.Key);
            }
            if ((table1[hash1] == null)) {
                table1[hash1] = entry;
                size++;
                return;
            } else {
                KeyValueEntry<Key, Value> temp;
                temp = table1[hash1];
                table1[hash1] = entry;
                entry = temp;
                hash0 = h0(entry.Key);
            }
        }
    }

    // resizeTable - HELPER METHOD: Adds Elements to the table after resizing
    private void addEntryToTable(KeyValueEntry<Key, Value>[] newTable, KeyValueEntry<Key, Value> entry, int newHash, List<KeyValueEntry<Key, Value>> collisionsList) {
        if (newTable[newHash] == null) {
            entry.maxSwaps = 0;
            newTable[newHash] = entry;
        } else {
            entry.maxSwaps = 0;
            collisionsList.add(entry);
        }
    }



    // determines if swaps should be on or off
    public void setSwapLogging(boolean state) {
        keySwapLogging = state;
    }

    // returns average amount of swaps for possibly the last 100 insertions
    public float getSwapAverage() {
        if (!keySwapLogging) return 0.0f;

        float sum = 0;
        for (int i = 0; i < Math.min(size, 100); i++) {
            sum += keySwapRecords[i].swaps;
        }

        return sum / (Math.min(size, 100));
    }

    // returns the variation of swaps of the possibly last 100 insertions
    public float getSwapVariation() {
        if (!keySwapLogging || size == 0) return 0.0f;

        float avg = getSwapAverage();
        float sumSquaredDifferences = 0;

        for (int i = 0; i < Math.min(size, 100); i++) {
            float difference = keySwapRecords[i].swaps - avg;
            sumSquaredDifferences += difference * difference;
        }

        return sumSquaredDifferences / (Math.min(size, 100)-1);
    }

    // HELPER METHOD: handles logic regarding swaps in normal cases
    private void swapLogic(KeyValueEntry<Key, Value> entry) {
        entry.maxSwaps++;
        if (keySwapLogging) {
            int index = keySwapIndex % 100;

            if (entry.swaps == -1) {
                entry.swaps++;
                keySwapRecords[index] = entry;
                entry.recordsIndex = index;
                keySwapIndex++;
            } else {
                if (keySwapRecords[entry.recordsIndex % 100 ] == entry) {
                    entry.swaps++;
                } else {
                    entry.swaps++;
                    keySwapRecords[index] = entry;
                    entry.recordsIndex = keySwapIndex % 100;
                    keySwapIndex++;
                }
            }
        }
    }

    // HELPER METHOD: handles logic regarding swaps for special cases -> replacing forgotten keys
    private void forgottenSwapLogic(KeyValueEntry<Key, Value> entry, KeyValueEntry<Key, Value> forgottenEntry) {
        if (!keySwapLogging) return;
        int index = forgottenEntry.recordsIndex % 100;

        if (keySwapRecords[index] == forgottenEntry) {
            keySwapRecords[index] = entry;
            entry.swaps++;
            entry.recordsIndex = index;
        } else {
            keySwapRecords[keySwapIndex % 100] = entry;
            entry.swaps++;
            entry.recordsIndex = keySwapIndex % 100;
            keySwapIndex++;
        }
    }

    // used to simulate the forgetting of the keys with time passage
    public void advanceTime(int hours) {
        currentTime = currentTime.plusHours(hours);
    }

    // time - HELPER METHOD: updates the timestamp of a certain key to the currentTime
    private void refreshTimeStamp(KeyValueEntry<Key, Value> entry) {
        entry.timeStamp = currentTime;
    }

    // time - HELPER METHOD: returns the time between current time and the key timestamp (used to know if a key should be forgotten)
    public int differenceOfTimes(LocalDateTime entryTimeStamp) {
        return (int) ChronoUnit.HOURS.between(entryTimeStamp, currentTime);
    }

    // iterator of KEYS
    // retorna o iterador
    public Iterable <Key> keys() {
        return new KeyIterator();
    }

    private class KeyIterator implements Iterator <Key>, Iterable <Key> {
        private int iteratedKeys;
        private int tableNumber;
        private int index;

        KeyIterator() {
            iteratedKeys = 0;
            tableNumber = 0;
            index = 0;
        }

        public boolean hasNext() {
            return iteratedKeys < size() && index < capacityTable1 - 1; // Returns true if the number of iterated keys is smaller than the number of keys
        }

        public Key next() { // Iterate keys
            if (!hasNext()) throw new NoSuchElementException();

            KeyValueEntry <Key, Value> [] currentTable = (tableNumber == 0) ? table0 : table1;

            while (index < currentTable.length && currentTable[index] == null) {
                index++;
            }

            if (index < currentTable.length) {
                iteratedKeys++;
                return currentTable[index++].Key;
            } else {
                tableNumber = 1 - tableNumber;
                index = 0;
                return next();
            }
        }

        public void remove() {
            throw new UnsupportedOperationException("Iterator doesn't support removal");
        }

        @Override
        public Iterator <Key> iterator() {
            return this;
        }
    }

    public static int xpto2(int n){
        int iterations = 0;
        for (int i = n; i > 0; i /= 2) {
            iterations++;
            for (int j = 0; j < n - 1; j++) {
            }
        }
    return iterations;
    }


    // Main for tests
    public static void main(String[] args) {
        /*
        ForgettingCuckooHashTable<String, Integer> hashtable = new ForgettingCuckooHashTable<>();

        hashtable.put("Helena", 10);
        System.out.println( hashtable.get("Helena"));
        hashtable.put("Helena", 20);
        System.out.println( hashtable.get("Helena"));

        printKeys(hashtable);

         */

        System.out.println(xpto2(4));

           // printKeySwapRecords(hashtable);
           // printAvgAndVariation(hashtable);
           // forgettingImpactTests();

           // timeTestsForgettingCuckooHashTable();
           // timeTestsOpenAddressingHashTable();
           // avgTimeTests();
    }

    /*
    // @TEST method to create and populate a Cuckoo Hash Table
    public static ForgettingCuckooHashTable <String, Integer> createHashTable(int n) {
        ForgettingCuckooHashTable<String, Integer> hashtable = new ForgettingCuckooHashTable<>();

        for (int i = 0; i < n; i++) {
            hashtable.setSwapLogging(true);
            hashtable.put(generateRandomString(7), i);
        }

        return hashtable;


    // @TEST method to perform time analysis tests on ForgettingCuckooHashTable
    public static void timeTestsForgettingCuckooHashTable() {
        Function<Integer, ForgettingCuckooHashTable<String, Integer>> hashtableCreator = ForgettingCuckooHashTable::createHashTable;

        Consumer<ForgettingCuckooHashTable<String, Integer>> consumerPut = hashtable -> {
            hashtable.put(generateRandomString(7),1);
        };

        Consumer<ForgettingCuckooHashTable<String, Integer>> consumerGet = hashtable -> {
            hashtable.get(generateRandomString(7));
        };

        TimeAnalysisUtils.runDoublingRatioTest(hashtableCreator, consumerPut, 17);
    }

     */

    /*
    // @TEST method to perform time analysis tests on Open Addressing Hash Table
    public static void timeTestsOpenAddressingHashTable() {
        Function<Integer, OpenAddressingHashTable<String, Integer>> openHashtableCreator = OpenAddressingHashTable::createHashTable;
        Consumer<OpenAddressingHashTable<String, Integer>> consumerPutOpenAddressing = hashtable -> {
            hashtable.put(generateRandomString(7),1);
        };

        Consumer<OpenAddressingHashTable<String, Integer>> consumerGetOpenAddressing = hashtable -> {
            hashtable.get(generateRandomString(7));
        };

        TimeAnalysisUtils.runDoublingRatioTest(openHashtableCreator, consumerPutOpenAddressing, 17);
    }
     */

    // @TEST method - prints every key inside the hashtable using the iterator
    private static void printKeys(ForgettingCuckooHashTable<String, Integer> hashtable) {
        int index = 1;
        for (String key: hashtable.keys()) {
            System.out.println("index: " + index++ + " " + key);
        }
    }

    // @TEST method - prints swap records for the last 100 keys inserted
    private static void printKeySwapRecords(ForgettingCuckooHashTable<String, Integer> hashtable) {
        int recordsPerLine = 10;
        System.out.println();

        for (int i = 0; i < Math.min(hashtable.size, 100); i++) {
            System.out.print(hashtable.keySwapRecords[i].swaps + " ");
            if ((i + 1) % recordsPerLine == 0) {
                System.out.println();
            }
        }
    }

    // @TEST method - prints average and variation values
    private static void printAvgAndVariation(ForgettingCuckooHashTable <String, Integer> hashtable) {
        hashtable.setSwapLogging(true);
        float avg = hashtable.getSwapAverage();
        float variation = hashtable.getSwapVariation();

        System.out.println(avg + "\t" + variation);
        hashtable.setSwapLogging(false);
    }

    // @TEST method to simulate time passage, to check if the entry was forgotten
    public static void advanceTimeTests(ForgettingCuckooHashTable <String,Integer> hashtable, int hours) {
        hashtable.put("a", 201);
        System.out.println(hashtable.get("a"));
        hashtable.advanceTime(hours);
        hashtable.put("a", 301);
        System.out.println(hashtable.get("a"));
    }

    /*
    // @TEST method to determine the impact forgetting has
    public static void forgettingImpactTests()
    {
        ArrayList<String> interestingKeys = new ArrayList<>(50000);
        for (int i = 0; i < 50000; i++)
            interestingKeys.add(generateRandomString(7));

        ArrayList<String> boringKeys = new ArrayList<>(200000);
        for (int i = 0; i < 200000; i++)
            boringKeys.add(generateRandomString(6));


        float avgTime = 0;
        float avgSwapAVG = 0;
        float avgVariation = 0;
        for (int j = 0; j < 20; j++) {
            ForgettingCuckooHashTable<String, Integer> hashTable = new ForgettingCuckooHashTable<>();

            float avg = 0;
            float swap = 0;
            float variation = 0;

            int interestingKeysIndex = 0;
            int boringKeysIndex = 0;

            long timeStart;
            long timeFinish;

            hashTable.setSwapLogging(true);

            for (int w = 0; w < 250000/1000; w++) {
                timeStart = System.currentTimeMillis();
                for (int i = 0; i < 200; i++) {
                    try {
                        hashTable.put(interestingKeys.get(interestingKeysIndex++), pseudoRandom.nextInt());
                    }catch (IllegalArgumentException ignored){}
                }
                for (int i = 0; i < 800; i++) {
                    try {
                        hashTable.put(boringKeys.get(boringKeysIndex++), pseudoRandom.nextInt());
                    }catch (IllegalArgumentException ignored){}
                }
                timeFinish = System.currentTimeMillis();
                avg += (timeFinish-timeStart);

                hashTable.advanceTime(2);

                int percentage = pseudoRandom.nextInt(0, 101); // 101 é exclusivo entao da valores entre 0 e 100

                if (percentage <= 1) {
                    for (int i = 0; i < 400; i++) {
                        try {
                            hashTable.put(interestingKeys.get(pseudoRandom.nextInt(0, interestingKeysIndex)), pseudoRandom.nextInt());
                        }catch (IllegalArgumentException ignored){}
                    }
                    for (int i = 0; i < 100; i++) {
                        try {
                            hashTable.put(boringKeys.get(pseudoRandom.nextInt(0, boringKeysIndex)), pseudoRandom.nextInt());
                        }catch (IllegalArgumentException ignored){}
                    }
                } else if (percentage <= 10) {
                    for (int i = 0; i < 400; i++) {
                        hashTable.containsKey(boringKeys.get(pseudoRandom.nextInt(0, interestingKeysIndex)));
                    }
                    for (int i = 0; i < 100; i++) {
                        hashTable.containsKey(boringKeys.get(pseudoRandom.nextInt(0, boringKeysIndex)));
                    }
                } else {
                    for (int i = 0; i < 400; i++) {
                        hashTable.get(interestingKeys.get(pseudoRandom.nextInt(0, interestingKeysIndex)));
                    }
                    for (int i = 0; i < 100; i++) {
                        hashTable.get(boringKeys.get(pseudoRandom.nextInt(0, boringKeysIndex)));
                    }
                }
            }
            swap = hashTable.getSwapAverage();
            variation = hashTable.getSwapVariation();
            avgTime += avg/250;
            avgSwapAVG += swap;
            avgVariation += variation;

        }
        System.out.println((avgTime/20) + "\t" + (avgSwapAVG/20) + "\t" + (avgVariation/20) +"\t");
    }

     */


    /*
    // @TEST method to test the average execution time of gets/puts methods
    public static void avgTimeTests() {

        float avgTime = 0;

        for (int j = 0; j < 20; j++) {
            OpenAddressingHashTable<String, Integer> hashTable = new OpenAddressingHashTable<>();
          //ForgettingCuckooHashTable<String, Integer> hashTable = new ForgettingCuckooHashTable<>();

            float avg = 0;

            long timeStart;
            long timeFinish;

            //hashTable.setSwapLogging(true);

            for (int w = 0; w < 50000 / 1000; w++) {
                //hashTable.advanceTime(2);
                timeStart = System.nanoTime();
                for (int i = 0; i < 1000; i++) {
                    try {
                        hashTable.put(generateRandomKey(), pseudoRandom.nextInt());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                timeFinish = System.nanoTime();

                for (int i = 0; i < 1000; i++) {
                    try {
                        hashTable.get(generateRandomKey());
                    } catch (IllegalArgumentException ignored) {
                    }
                }


                avg += (timeFinish - timeStart)/1000;

                avgTime += (avg / 50);

            }
           // hashTable.setSwapLogging(false);
        }
            System.out.println((avgTime / 20));
    }

     */

/*
    // @TEST method generates random *different* strings
    public static String generateRandomString(int size)
    {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'


        String generatedString = pseudoRandom.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(size)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        return generatedString;
    }

 */

    // @TEST method generates random string from 1 to 5 chars, with random chars from a-z and 1-9
    public static String generateRandomKey() {
        Random random = new Random();
        int length = random.nextInt(14)+7;
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < length; i++) {
            char randomChar;
            if (random.nextBoolean()) {
                randomChar = (char)('a' + random.nextInt(26));
            } else {
                randomChar = (char)('0' + random.nextInt(10));
            }
            stringBuilder.append(randomChar);
        }

        return stringBuilder.toString();
    }
}