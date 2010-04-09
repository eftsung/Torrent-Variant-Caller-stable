package org.broadinstitute.sting.utils.fasta;

import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.broadinstitute.sting.BaseTest;

import java.io.File;
import java.io.FileNotFoundException;

import net.sf.picard.reference.ReferenceSequence;
import net.sf.picard.reference.ReferenceSequenceFile;
import net.sf.picard.reference.ReferenceSequenceFileFactory;
import net.sf.picard.PicardException;
import net.sf.samtools.util.StringUtil;

/**
 * Test the indexed fasta sequence file reader.
 */
public class IndexedFastaSequenceFileUnitTest extends BaseTest {
    private static String sequenceFileName;
    private IndexedFastaSequenceFile sequenceFile = null;

    private final String firstBasesOfChrM = "GATCACAGGTCTATCACCCT";
    private final String extendedBasesOfChrM = "GATCACAGGTCTATCACCCTATTAACCACTCACGGGAGCTCTCCATGCAT" +
                                               "TTGGTATTTTCGTCTGGGGGGTGTGCACGCGATAGCATTGCGAGACGCTG" +
                                               "GAGCCGGAGCACCCTATGTCGCAGTATCTGTCTTTGATTCCTGCCTCATT";    
    private final String firstBasesOfChr1 = "taaccctaaccctaacccta";
    private final String firstBasesOfChr8 = "GCAATTATGACACAAAAAAT";

    @BeforeClass
    public static void initialize() {
         sequenceFileName = seqLocation + "/references/Homo_sapiens_assembly18/v0/Homo_sapiens_assembly18.fasta";
    }

    @Before
    public void doForEachTest() throws FileNotFoundException {
        sequenceFile = new IndexedFastaSequenceFile( new File(sequenceFileName) );
    }

    @Test
    public void testOpenFile() {
        long startTime = System.currentTimeMillis();
        Assert.assertNotNull( sequenceFile );
        long endTime = System.currentTimeMillis();

        System.err.printf("testOpenFile runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test
    public void testFirstSequence() {
        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chrM",1,firstBasesOfChrM.length());
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", sequence.getName(), "chrM");
        Assert.assertEquals("Sequence contig index is not correct", sequence.getContigIndex(), 0);
        Assert.assertArrayEquals( "First n bases of chrM are incorrect",StringUtil.stringToBytes(firstBasesOfChrM),sequence.getBases());

        System.err.printf("testFirstSequence runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test
    public void testFirstSequenceExtended() {
        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chrM",1,extendedBasesOfChrM.length());
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", sequence.getName(), "chrM");
        Assert.assertEquals("Sequence contig index is not correct", sequence.getContigIndex(), 0);
        Assert.assertArrayEquals( "First n bases of chrM are incorrect",StringUtil.stringToBytes(extendedBasesOfChrM),sequence.getBases());

        System.err.printf("testFirstSequenceExtended runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test
    public void testReadStartingInCenterOfFirstLine() {
        final int bytesToChopOff = 5;
        String truncated = extendedBasesOfChrM.substring(bytesToChopOff);

        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chrM",
                                                                   bytesToChopOff + 1,
                                                                   bytesToChopOff + truncated.length());
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", sequence.getName(), "chrM");
        Assert.assertEquals("Sequence contig index is not correct", sequence.getContigIndex(), 0);
        Assert.assertArrayEquals( "First n bases of chrM are incorrect",StringUtil.stringToBytes(truncated),sequence.getBases());

        System.err.printf("testReadStartingInCenterOfFirstLine runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test
    public void testReadStartingInCenterOfMiddleLine() {
        final int bytesToChopOff = 120;
        String truncated = extendedBasesOfChrM.substring(bytesToChopOff);

        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chrM",
                                                                   bytesToChopOff + 1,
                                                                   bytesToChopOff + truncated.length());
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", sequence.getName(), "chrM");
        Assert.assertEquals("Sequence contig index is not correct", sequence.getContigIndex(), 0);
        Assert.assertArrayEquals( "First n bases of chrM are incorrect",StringUtil.stringToBytes(truncated),sequence.getBases());

        System.err.printf("testReadStartingInCenterOfMiddleLine runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test
    public void testFirstCompleteContigRead() {
        ReferenceSequenceFile originalSequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(new File(sequenceFileName));
        ReferenceSequence expectedSequence = originalSequenceFile.nextSequence();

        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSequence("chrM");
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", sequence.getName(), "chrM");
        Assert.assertEquals("Sequence contig index is not correct", sequence.getContigIndex(), 0);
        Assert.assertArrayEquals("chrM is incorrect",expectedSequence.getBases(),sequence.getBases());

        System.err.printf("testFirstCompleteContigRead runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(expected= PicardException.class)
    public void testReadThroughEndOfContig() {
        long startTime = System.currentTimeMillis();
        try {
            ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chrM",16500,16600);
        }
        finally {
            long endTime = System.currentTimeMillis();
            System.err.printf("testReadThroughEndOfContig runtime: %dms%n", (endTime - startTime)) ;
        }
    }

    @Test(expected= PicardException.class)
    public void testReadPastEndOfContig() {
         long startTime = System.currentTimeMillis();
         try {
             ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chrM",16800,16900);
         }
         finally {
             long endTime = System.currentTimeMillis();
             System.err.printf("testReadPastEndOfContig runtime: %dms%n", (endTime - startTime)) ;
         }
     }

    @Test
    public void testMiddleCompleteContigRead() {
        ReferenceSequenceFile originalSequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(new File(sequenceFileName));
        ReferenceSequence expectedSequence = originalSequenceFile.nextSequence();
        while( !expectedSequence.getName().equals("chrY") )
            expectedSequence = originalSequenceFile.nextSequence();

        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSequence("chrY");
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", sequence.getName(), "chrY");
        Assert.assertEquals("Sequence contig index is not correct", sequence.getContigIndex(), 24);
        Assert.assertArrayEquals("chrY is incorrect",expectedSequence.getBases(),sequence.getBases());

        System.err.printf("testMiddleCompleteContigRead runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test
    public void testLastCompleteContigRead() {
        ReferenceSequenceFile originalSequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(new File(sequenceFileName));
        ReferenceSequence expectedSequence = originalSequenceFile.nextSequence();
        while( !expectedSequence.getName().equals("chrX_random") )
            expectedSequence = originalSequenceFile.nextSequence();

        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSequence("chrX_random");
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", sequence.getName(), "chrX_random");
        Assert.assertEquals("Sequence contig index is not correct", sequence.getContigIndex(), 44);
        Assert.assertArrayEquals("chrX_random is incorrect",expectedSequence.getBases(),sequence.getBases());

        System.err.printf("testLastCompleteContigRead runtime: %dms%n", (endTime - startTime)) ;
    }


    @Test
    public void testFirstOfChr1() {
        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chr1",1,firstBasesOfChr1.length());
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", sequence.getName(), "chr1");
        Assert.assertEquals("Sequence contig index is not correct", sequence.getContigIndex(), 1);
        Assert.assertArrayEquals( "First n bases of chr1 are incorrect",StringUtil.stringToBytes(firstBasesOfChr1),sequence.getBases());

        System.err.printf("testFirstOfChr1 runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test
    public void testFirstOfChr8() {
        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chr8",1,firstBasesOfChr8.length());
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", sequence.getName(), "chr8");
        Assert.assertEquals("Sequence contig index is not correct", sequence.getContigIndex(), 8);
        Assert.assertArrayEquals( "First n bases of chr8 are incorrect",StringUtil.stringToBytes(firstBasesOfChr8),sequence.getBases());

        System.err.printf("testFirstOfChr8 runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test
    public void testFirstElementOfIterator() {
        ReferenceSequenceFile originalSequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(new File(sequenceFileName));
        ReferenceSequence expectedSequence = originalSequenceFile.nextSequence();

        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.nextSequence();
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", sequence.getName(), "chrM");
        Assert.assertEquals("Sequence contig index is not correct", sequence.getContigIndex(), 0);
        Assert.assertArrayEquals("chrM is incorrect",expectedSequence.getBases(),sequence.getBases());

        System.err.printf("testFirstElementOfIterator runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test
    public void testNextElementOfIterator() {
        ReferenceSequenceFile originalSequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(new File(sequenceFileName));
        // Skip past the first one and load the second one.
        originalSequenceFile.nextSequence();
        ReferenceSequence expectedSequence = originalSequenceFile.nextSequence();

        long startTime = System.currentTimeMillis();
        sequenceFile.nextSequence();
        ReferenceSequence sequence = sequenceFile.nextSequence();
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", "chr1", sequence.getName());
        Assert.assertEquals("Sequence contig index is not correct", 1, sequence.getContigIndex());
        Assert.assertEquals("Sequence size is not correct", expectedSequence.length(), sequence.length());
        Assert.assertArrayEquals("chr1 is incorrect",expectedSequence.getBases(),sequence.getBases());

        System.err.printf("testNextElementOfIterator runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test
    public void testReset() {
        ReferenceSequenceFile originalSequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(new File(sequenceFileName));
        // Skip past the first one and load the second one.
        ReferenceSequence expectedSequence = originalSequenceFile.nextSequence();

        long startTime = System.currentTimeMillis();
        sequenceFile.nextSequence();
        sequenceFile.nextSequence();
        sequenceFile.reset();
        ReferenceSequence sequence = sequenceFile.nextSequence();
        long endTime = System.currentTimeMillis();

        Assert.assertEquals("Sequence contig is not correct", "chrM", sequence.getName());
        Assert.assertEquals("Sequence contig index is not correct", 0, sequence.getContigIndex());
        Assert.assertEquals("Sequence size is not correct", expectedSequence.length(), sequence.length());
        Assert.assertArrayEquals("chrM is incorrect", expectedSequence.getBases(),sequence.getBases());

        System.err.printf("testReset runtime: %dms%n", (endTime - startTime)) ;
    }
}