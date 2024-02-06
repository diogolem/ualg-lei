
package aed.tables;

public class OpenAddressingHashTable <Key, Value> {

    private static int[] primes = {
            17, 37, 79, 163, 331, 673, 1361, 2729, 5471, 10949, 21911,
            43853, 87719, 175447, 350899, 701819, 1403641, 2807303,
            5614657, 11229331, 22458671, 44917381, 89834777, 179669557};

    private int m;
    private int primeIndex;
    private int size;
    private float loadFactor;

    private Key[] keys;
    private Value[] values;

    @SuppressWarnings("unchecked")
    private OpenAddressingHashTable(int primeIndex)
    {
        this.primeIndex = primeIndex;
        this.m = this.primes[primeIndex];
        this.size = 0; this.loadFactor = 0;
        this.keys = (Key[]) new Object[this.m];
        this.values = (Value[]) new Object[this.m];
    }

    public OpenAddressingHashTable(){
        this(0);}


    private int hash(Key k)
    {
        return (k.hashCode() & 0x7fffffff) % this.m;
    }

    public Value get(Key k)
    {
        for(int i = hash(k); this.keys[i] != null; i = (i+1)%this.m)
        {
        //key was found, return its value
            if(this.keys[i].equals(k))
            {
                return this.values[i];
            }
        }
        return null;
    }

    public void put(Key k, Value v)
    {
        if(this.loadFactor >= 0.5f)
        {
            resize(this.primeIndex+1);
        }
        int i = hash(k);
        for(; this.keys[i] != null; i = (i+1)% this.m)
        {
        //key was found, update its value
            if(this.keys[i].equals(k))
            {
                this.values[i] = v;
                return;
            }
        }
        //we've found the right insertion position, insert
        this.keys[i] = k;
        this.values[i] = v;
        this.size++;
        this.loadFactor = this.size/this.m;
    }

    private void resize(int primeIndex)
    {
    //if invalid size do not resize;
        if(primeIndex < 0 || primeIndex >= primes.length) return;
        this.primeIndex = primeIndex;
        OpenAddressingHashTable<Key,Value> aux =
                new OpenAddressingHashTable<Key,Value>(this.primeIndex);
    //place all existing keys in new table
        for(int i = 0; i < this.m; i++)
        {
            if(keys[i] != null) aux.put(keys[i],values[i]);
        }
        this.keys = aux.keys;
        this.values = aux.values;
        this.m = aux.m;
        this.loadFactor = this.size/this.m;
    }

    public static OpenAddressingHashTable <String, Integer> createHashTable(int n) {
        OpenAddressingHashTable<String, Integer> hashtable = new OpenAddressingHashTable<>();

        for (int i = 0; i < n; i++) {
            hashtable.put(ForgettingCuckooHashTable.generateRandomString(7), i);
        }

        return hashtable;
    }
}
