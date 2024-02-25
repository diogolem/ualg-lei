package aed.sorting;

import aed.utils.TimeAnalysisUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Collections;
import java.util.function.Consumer;

class Limits
{
    char minChar;
    char maxChar;
    int maxLength;
}

public class RecursiveStringSort extends Sort {

    private static final Random R = new Random();

    public static <T extends Comparable<T>> void quicksort(T[] a)
    {
        qsort(a, 0, a.length-1);
    }

    private static <T extends Comparable<T>> void qsort(T[] a, int low, int high)
    {
        if (high <= low) return;
        int j = partition(a, low, high);
        qsort(a, low, j-1);
        qsort(a, j+1, high);
    }

    private static <T extends Comparable<T>> int partition(T[] a, int low, int high)
    {
        //partition into a[low...j-1],a[j],[aj+1...high] and return j
        //choose a random pivot
        int pivotIndex = low + R.nextInt(high+1-low);
        exchange(a,low,pivotIndex);
        T v = a[low];
        int i = low, j = high +1;

        while(true)
        {
            while(less(a[++i],v)) if(i == high) break;
            while(less(v,a[--j])) if(j == low) break;

            if(i >= j) break;
            exchange(a , i, j);
        }
        exchange(a, low, j);

        return j;
    }

    // Insertion Sort for strings
    public static void insertionSort(List<String> a) {
        int n = a.size();
        for (int i = 1; i < n; i++) {
            for (int j = i; j > 0; j--) {
                if (a.get(j-1).compareTo(a.get(j)) > 0) {
                    Collections.swap(a, j, j - 1);
                } else break;
            }
        }
    }

    // Determine the lowest ascii char in a certain position of every string of the list
    public static Limits determineLimits(List<String> a, int characterIndex) {
        Limits limits = new Limits();

        if (a.isEmpty()) {
            limits.minChar = Character.MIN_VALUE;
        } else { // O char index > str.length
            limits.minChar = Character.MAX_VALUE;
        }

        for (String str : a) { 
            if (charIndexIsValid(characterIndex, str)) { // char index 0 - é valido
                char currentChar = str.charAt(characterIndex);
                limits.minChar = (char) Math.min(limits.minChar, currentChar);
                limits.maxChar = (char) Math.max(limits.maxChar, currentChar);
            } else { limits.minChar = 0; }
            limits.maxLength = Math.max(limits.maxLength, str.length());
        }
        return limits;
    }

    private static boolean charIndexIsValid(int characterIndex, String str) {
        return characterIndex < str.length();
    }

    public static void sort(String[] a) {
        recursive_sort(Arrays.asList(a), 0);
    }

    // Método de Ordenação principal
    public static void recursive_sort(List<String> a, int characterIndex) {
        // Call insertion sort if the sample size is not big enough to pay the memory used in the recursive sort
        if (a.size() < 50) {
            insertionSort(a);
            return;
        }

        Limits limits = determineLimits(a, characterIndex); // min char: 0  - max char: a (1)

        // Create array of buckets (arrays)
        @SuppressWarnings("unchecked")
        ArrayList<String>[] buckets = new ArrayList[limits.maxChar - limits.minChar + 1];
        ArrayList<String> zeroBucket = new ArrayList<>();

        // Will create different buckets for every different firstChar
        for (String str : a) {
            if (characterIndex < str.length()) { //aa
                int firstChar = str.charAt(characterIndex) - limits.minChar;
                if (buckets[firstChar] == null) {
                    buckets[firstChar] = new ArrayList<>();
                }
                buckets[firstChar].add(str);
            } else {
                zeroBucket.add(str);
            }
        }

        // Order each bucket
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] != null) { // buckets[0] ? "aa,a!"
                recursive_sort(buckets[i], characterIndex + 1);
            }
        }

        // Reinsert sorted original list
        int index = 0;
        for (String str : zeroBucket) {
            a.set(index++, str);
        }
        for (ArrayList<String> bucket : buckets) {
            if (bucket != null) {
                for (String str : bucket) {
                    a.set(index++, str);
                }
            }
        }
    }

    /** It's very hard to create a sorting algorithm faster than the recursive_sort without compromising
     integrity of the code. This version of the recursive_sort will give the algorithm a easier time when most of the strings are in english
     but there are some in (for example), japanese characters, this happens because on the original algorithm, if there's a big descrepancy
     between the coded values of the characters, the array will iterate this difference amount of times, checking for a valid bucket,
     in extreme cases it could be iterating 200 times for each bucket, just to find the valid one. This version will close this gap
     by initially separating these cases into smaller ones.
     */
    public static void fasterSort(String[] a) {
        if (a.length <= 1) return;

        @SuppressWarnings("unchecked")
        List<String>[] buckets = new ArrayList[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
        ArrayList<String> zeroBucket = new ArrayList<>();

        for (String str : a) {
            if (!str.isEmpty()) {
                char firstChar = str.charAt(0);
                if (firstChar < 32) { buckets[0].add(str); }
                else if (firstChar <= 127) { buckets[1].add(str); }
                else { buckets[2].add(str); }
            } else { zeroBucket.add(str); }
        }

        for (List<String> bucket : buckets) {
            recursive_sort(bucket,0);
        }

        int index = 0;
        for (String str : zeroBucket) a[index++] = str;
        for (List<String> bucket : buckets) {
            for (String str : bucket) { a[index++] = str; }
        }
    }

    public static String[] geraRecursiveStringList(int n) {
        Random random = new Random();
        String[] stringList = new String[n];

        for (int i = 0; i < n; i++) {
            int size = random.nextInt(10) + 1;
            StringBuilder randomString = new StringBuilder();

            for (int j = 0; j < size; j++) {
                char randomChar = (char) (random.nextInt());
                randomString.append(randomChar);
            }
            stringList[i] = randomString.toString();
        }
        return stringList;
    }

    // Method created in TimeAnalysisUtils to run doubling ratio tests for memory
    /*public static<T> void runDoublingRatioTestMemory(Function<Integer,T> exampleGenerator, Consumer<T> methodToTest, int iterations)
    {
        assert(iterations > 0);
        int n = MINIMUM_COMPLEXITY;
        double previousTime = getAverageCPUTime(exampleGenerator,n,methodToTest,DEFAULT_TRIALS);
        System.out.println("i\tcomplexity\ttime(ms)\tmemory");
        System.out.println("0\t" + n + "\t" + previousTime + "\t ---");
        double newTime;
        long startMemory;
        long endMemory;
        long memoryUsed;

        for(int i = 0; i < iterations; i++)
        {
            System.gc();
            n*=2;
            startMemory = Runtime.getRuntime().maxMemory() - Runtime.getRuntime().freeMemory();
            newTime = getAverageCPUTime(exampleGenerator,n,methodToTest,DEFAULT_TRIALS);
            endMemory = Runtime.getRuntime().maxMemory() - Runtime.getRuntime().freeMemory();
            memoryUsed = (endMemory - startMemory)/30;
            System.out.println(i+1 + "\t" + n + "\t" + newTime/1E6 + "\t" + memoryUsed/(1024*1024) + "mb");
        }
    }
     */


    private static void timeTests() {
        Consumer<String[]> consumerRecursiveSort = array -> {
            RecursiveStringSort.sort(array);
        };

        Consumer<String[]> consumerQuickSort = array -> {
            RecursiveStringSort.fasterSort(array);
        };

        //TimeAnalysisUtils.runDoublingRatioTest(RecursiveStringSort::geraRecursiveStringList, consumerRecursiveSort, 10);
        //TimeAnalysisUtils.runDoublingRatioTestMemory(RecursiveStringSort::geraRecursiveStringList, consumerRecursiveSort, 14);
    }

    public static void main(String[] args) {
        //timeTests();
        String[] inputArray = {"aa","a", "bbb", "bb", "b" ,"apple", "orange", "grape", "kiwi"};
        RecursiveStringSort.sort(inputArray);
    }
}
