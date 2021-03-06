/*
 * Copyright (c) 2011, The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.broadinstitute.sting.gatk.walkers.varianteval.evaluators;

import net.sf.picard.util.IntervalTree;
import org.apache.log4j.Logger;
import org.broadinstitute.sting.gatk.contexts.AlignmentContext;
import org.broadinstitute.sting.gatk.contexts.ReferenceContext;
import org.broadinstitute.sting.gatk.refdata.RefMetaDataTracker;
import org.broadinstitute.sting.gatk.walkers.varianteval.VariantEvalWalker;
import org.broadinstitute.sting.gatk.walkers.varianteval.util.Analysis;
import org.broadinstitute.sting.gatk.walkers.varianteval.util.DataPoint;
import org.broadinstitute.sting.utils.GenomeLoc;
import org.broadinstitute.sting.utils.codecs.vcf.VCFConstants;
import org.broadinstitute.sting.utils.exceptions.UserException;
import org.broadinstitute.sting.utils.interval.IntervalUtils;
import org.broadinstitute.sting.utils.variantcontext.Genotype;
import org.broadinstitute.sting.utils.variantcontext.VariantContext;
import org.broadinstitute.sting.utils.variantcontext.VariantContextUtils;

import java.util.*;

@Analysis(description = "1000 Genomes Phase I summary of variants table")
public class VariantSummary extends VariantEvaluator implements StandardEval {
    final protected static Logger logger = Logger.getLogger(VariantSummary.class);

    /** Indels with size greater than this value are tallied in the CNV column */
    private final static int MAX_INDEL_LENGTH = 50;
    private final static double MIN_CNV_OVERLAP = 0.5;
    private VariantEvalWalker walker;

    public enum Type {
        SNP, INDEL, CNV
    }

    Map<String, IntervalTree<GenomeLoc>> knownCNVs = null;

    // basic counts on various rates found
    @DataPoint(description = "Number of samples")
    public long nSamples = 0;

    @DataPoint(description = "Number of processed loci")
    public long nProcessedLoci = 0;

    @DataPoint(description = "Number of SNPs")
    public long nSNPs = 0;
    @DataPoint(description = "Overall TiTv ratio", format = "%.2f")
    public double TiTvRatio = 0;
    @DataPoint(description = "SNP Novelty Rate")
    public String SNPNoveltyRate = "NA";
    @DataPoint(description = "Mean number of SNPs per individual")
    public long nSNPsPerSample = 0;
    @DataPoint(description = "Mean TiTv ratio per individual", format = "%.2f")
    public double TiTvRatioPerSample = 0;
    @DataPoint(description = "Mean depth of coverage per sample at SNPs", format = "%.1f")
    public double SNPDPPerSample = 0;

    @DataPoint(description = "Number of Indels")
    public long nIndels = 0;
    @DataPoint(description = "Indel Novelty Rate")
    public String IndelNoveltyRate = "NA";
    @DataPoint(description = "Mean number of Indels per individual")
    public long nIndelsPerSample = 0;
    @DataPoint(description = "Mean depth of coverage per sample at Indels", format = "%.1f")
    public double IndelDPPerSample = 0;

    @DataPoint(description = "Number of SVs")
    public long nSVs = 0;
    @DataPoint(description = "SV Novelty Rate")
    public String SVNoveltyRate = "NA";
    @DataPoint(description = "Mean number of SVs per individual")
    public long nSVsPerSample = 0;

    TypeSampleMap allVariantCounts, knownVariantCounts;
    TypeSampleMap countsPerSample;
    TypeSampleMap transitionsPerSample, transversionsPerSample;
    TypeSampleMap depthPerSample;

    private final static String ALL = "ALL";

    private class TypeSampleMap extends EnumMap<Type, Map<String, Integer>> {
        public TypeSampleMap(final Collection<String> samples) {
            super(Type.class);
            for ( Type type : Type.values() ) {
                Map<String, Integer> bySample = new HashMap<String, Integer>(samples.size());
                for ( final String sample : samples ) {
                    bySample.put(sample, 0);
                }
                bySample.put(ALL, 0);
                this.put(type, bySample);
            }
        }

        public final void inc(final Type type, final String sample) {
            final int count = this.get(type).get(sample);
            get(type).put(sample, count + 1);
        }

        public final int all(Type type) {
            return get(type).get(ALL);
        }

        public final int meanValue(Type type) {
            long sum = 0;
            int n = 0;
            for ( final Map.Entry<String, Integer> pair : get(type).entrySet() ) {
                if ( pair.getKey() != ALL)  {
                    n++;
                    sum += pair.getValue();
                }
            }
            return (int)(Math.round(sum / (1.0 * n)));
        }

        public final double ratioValue(Type type, TypeSampleMap denoms, boolean allP) {
            double sum = 0;
            int n = 0;
            for ( final String sample : get(type).keySet() ) {
                if ( (allP && sample == ALL) || (!allP && sample != ALL) ) {
                    final long num = get(type).get(sample);
                    final long denom = denoms.get(type).get(sample);
                    sum += ratio(num, denom);
                    n++;
                }
            }

            return n > 0 ? sum / (1.0 * n) : 0.0;
        }
    }


    public void initialize(VariantEvalWalker walker) {
        this.walker = walker;

        nSamples = walker.getSampleNamesForEvaluation().size();
        countsPerSample = new TypeSampleMap(walker.getSampleNamesForEvaluation());
        transitionsPerSample = new TypeSampleMap(walker.getSampleNamesForEvaluation());
        transversionsPerSample = new TypeSampleMap(walker.getSampleNamesForEvaluation());
        allVariantCounts = new TypeSampleMap(walker.getSampleNamesForEvaluation());
        knownVariantCounts = new TypeSampleMap(walker.getSampleNamesForEvaluation());
        depthPerSample = new TypeSampleMap(walker.getSampleNamesForEvaluation());

        if ( walker.knownCNVsFile != null ) {
            knownCNVs = walker.createIntervalTreeByContig(walker.knownCNVsFile);
            final List<GenomeLoc> locs = walker.knownCNVsFile.getIntervals(walker.getToolkit());
            logger.info(String.format("Creating known CNV list %s containing %d intervals covering %d bp",
                    walker.knownCNVsFile.getSource(), locs.size(), IntervalUtils.intervalSize(locs)));
        }
    }

    @Override public boolean enabled() { return true; }

    public int getComparisonOrder() {
        return 2;   // we only need to see each eval track
    }

    public void update0(RefMetaDataTracker tracker, ReferenceContext ref, AlignmentContext context) {
        nProcessedLoci += context.getSkippedBases() + (ref == null ? 0 : 1);
    }

    private final Type getType(VariantContext vc) {
        switch (vc.getType()) {
            case SNP:
                return Type.SNP;
            case INDEL:
                for ( int l : vc.getIndelLengths() )
                    if ( Math.abs(l) > MAX_INDEL_LENGTH )
                        return Type.CNV;
                return Type.INDEL;
            case SYMBOLIC:
                return Type.CNV;
            default:
                throw new UserException.BadInput("Unexpected variant context type: " + vc);
        }
    }

    private final boolean overlapsKnownCNV(VariantContext cnv) {
        if ( knownCNVs != null ) {
            final GenomeLoc loc = walker.getGenomeLocParser().createGenomeLoc(cnv, true);
            IntervalTree<GenomeLoc> intervalTree = knownCNVs.get(loc.getContig());

            final Iterator<IntervalTree.Node<GenomeLoc>> nodeIt = intervalTree.overlappers(loc.getStart(), loc.getStop());
            while ( nodeIt.hasNext() ) {
                final double overlapP = loc.reciprocialOverlapFraction(nodeIt.next().getValue());
                if ( overlapP > MIN_CNV_OVERLAP )
                    return true;
            }
        }

        return false;
    }

    public String update2(VariantContext eval, VariantContext comp, RefMetaDataTracker tracker, ReferenceContext ref, AlignmentContext context) {
        if ( eval == null || eval.isMonomorphicInSamples() ) return null;

        final Type type = getType(eval);

        TypeSampleMap titvTable = null;

        // update DP, if possible
        if ( eval.hasAttribute(VCFConstants.DEPTH_KEY) )
            depthPerSample.inc(type, ALL);

        // update counts
        allVariantCounts.inc(type, ALL);

        // type specific calculations
        if ( type == Type.SNP && eval.isBiallelic() ) {
            titvTable = VariantContextUtils.isTransition(eval) ? transitionsPerSample : transversionsPerSample;
            titvTable.inc(type, ALL);
        }

        // novelty calculation
        if ( comp != null || (type == Type.CNV && overlapsKnownCNV(eval)))
            knownVariantCounts.inc(type, ALL);

        // per sample metrics
        for (final Genotype g : eval.getGenotypes()) {
            if ( ! g.isNoCall() && ! g.isHomRef() ) {
                countsPerSample.inc(type, g.getSampleName());

                // update transition / transversion ratio
                if ( titvTable != null ) titvTable.inc(type, g.getSampleName());

                if ( g.hasAttribute(VCFConstants.DEPTH_KEY) )
                    depthPerSample.inc(type, g.getSampleName());
            }
        }

        return null; // we don't capture any interesting sites
    }

    private final String noveltyRate(Type type) {
        final int all = allVariantCounts.all(type);
        final int known = knownVariantCounts.all(type);
        final int novel = all - known;
        final double rate = (novel / (1.0 * all));
        return all == 0 ? "NA" : String.format("%.2f", rate);
    }

    public void finalizeEvaluation() {
        nSNPs = allVariantCounts.all(Type.SNP);
        nIndels = allVariantCounts.all(Type.INDEL);
        nSVs = allVariantCounts.all(Type.CNV);

        TiTvRatio = transitionsPerSample.ratioValue(Type.SNP, transversionsPerSample, true);
        TiTvRatioPerSample = transitionsPerSample.ratioValue(Type.SNP, transversionsPerSample, false);

        nSNPsPerSample = countsPerSample.meanValue(Type.SNP);
        nIndelsPerSample = countsPerSample.meanValue(Type.INDEL);
        nSVsPerSample = countsPerSample.meanValue(Type.CNV);

        SNPNoveltyRate = noveltyRate(Type.SNP);
        IndelNoveltyRate = noveltyRate(Type.INDEL);
        SVNoveltyRate = noveltyRate(Type.CNV);

        SNPDPPerSample = depthPerSample.meanValue(Type.SNP);
        IndelDPPerSample = depthPerSample.meanValue(Type.INDEL);
    }
}