package org.janelia.thickness.inference;

import java.util.ArrayList;
import java.util.TreeMap;

import mpicbg.models.AffineModel1D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.list.ListImg;
import net.imglib2.outofbounds.OutOfBounds;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.TransformView;
import net.imglib2.view.Views;

import org.janelia.thickness.EstimateQualityOfSlice;
import org.janelia.thickness.LocalizedCorrelationFit;
import org.janelia.thickness.ShiftCoordinates;
import org.janelia.thickness.cluster.Categorizer;
import org.janelia.thickness.cluster.RangedCategorizer;
import org.janelia.thickness.inference.visitor.Visitor;
import org.janelia.thickness.lut.LUTRealTransform;
import org.janelia.thickness.lut.PermutationTransform;
import org.janelia.thickness.mediator.OpinionMediator;
import org.janelia.utility.arrays.ArraySortedIndices;
import org.janelia.utility.tuple.ConstantPair;

public class InferFromMatrix< M extends Model<M> > {

	private final M correlationFitModel;
    private final OpinionMediator shiftMediator;


    public InferFromMatrix(
            final M correlationFitModel,
            final OpinionMediator shiftMediator ) {
            super();

            this.correlationFitModel = correlationFitModel;
            this.shiftMediator = shiftMediator;

           
    }


    public < T extends RealType< T > > double[] estimateZCoordinates(  
    		final RandomAccessibleInterval< T > matrix,
    		final double[] startingCoordinates,
    		final Options options
    		) throws NotEnoughDataPointsException, IllDefinedDataPointsException {
            return estimateZCoordinates( 
        		matrix, 
        		startingCoordinates, 
        		new Visitor() {

        			@Override
					public < U extends RealType< U > > void act(final int iteration,
							final RandomAccessibleInterval<U> matrix, final double[] lut,
							final int[] permutation, final int[] inversePermutation,
							final double[] multipliers, final double[] weights,
							final RandomAccessibleInterval< double[] > estimatedFit) {
						// don't do anything
					}
		        	
		        },
		        options );
    }
    
    public < T extends RealType< T > > double[] estimateZCoordinates(
            final RandomAccessibleInterval< T > input,
            final double[] startingCoordinates,
            final Visitor visitor,
            final Options options) throws NotEnoughDataPointsException, IllDefinedDataPointsException {
    	final RangedCategorizer categorizer = new RangedCategorizer( startingCoordinates.length );
    	return estimateZCoordinates( input, startingCoordinates, visitor, categorizer, options );
    }


    public < T extends RealType< T > > double[] estimateZCoordinates(
            final RandomAccessibleInterval< T > inputMatrix,
            final double[] startingCoordinates,
            final Visitor visitor,
            final Categorizer categorizer,
            final Options options) throws NotEnoughDataPointsException, IllDefinedDataPointsException {
    	
    	final double[] lut         = startingCoordinates.clone();
    	final int n                = (int) inputMatrix.dimension( 0 );
    	final int[] permutationLut = new int[ n ];
    	final int[] inverse        = permutationLut.clone();
    	final int nMatrixDim       = inputMatrix.numDimensions();
    	final double[] weights     = new double[ n ];
    	for (int i = 0; i < weights.length; i++) {
			weights[ i ] = 1.0;
		}
    	final double multipliers[] = weights.clone();
    	
    	for ( int iteration = 0; iteration < options.nIterations; ++iteration ) {
    		
    		final double[] permutedLut = lut.clone();
    		ArraySortedIndices.sort( permutedLut, permutationLut, inverse );
    		final PermutationTransform permutation       = new PermutationTransform( permutationLut, nMatrixDim, nMatrixDim );
    		final IntervalView<T> matrix                 = Views.interval( new TransformView< T >( inputMatrix, permutation ), inputMatrix );
//    		final IntervalView<DoubleType> currentLutImg = Views.interval( new TransformView< DoubleType >( ArrayImgs.doubles( lut, n ), permutation ), new FinalInterval( n ) ); // use this?
    		
    		final double[] shifts = this.getMediatedShifts(
    				matrix, 
    				permutedLut, 
    				multipliers, 
    				weights, 
    				iteration, 
    				categorizer, 
    				options);
    		
    		this.applyShifts( 
    				lut, // rewrite interface to use view on permuted lut?
    				shifts, 
    				multipliers, 
    				permutation.copyToDimension( 1, 1 ), 
    				options);
    		
    		final boolean preventReorder = !options.withReorder;
    		if ( preventReorder )
    			preventReorder( lut, permutation, options ); // rewrite interface to use view on permuted lut?
    		
    		if ( options.withRegularization )
    			regularize( lut, inverse, options );
    		
   		
        		visitor.act( iteration + 1, matrix, lut, permutationLut, inverse, multipliers, weights, null );
    		
    	}
    	
    	return lut;
    }
        	

    public < T extends RealType< T > > double[] getMediatedShifts ( 
    		final RandomAccessibleInterval< T > matrix,
    		final double[] lut,
    		final double[] multipliers,
            final double[] weights,
            final int iteration,
            final Categorizer categorizer,
            final Options options
            ) throws NotEnoughDataPointsException, IllDefinedDataPointsException {
    	
    	final int nMatrixDimensions      = matrix.numDimensions();
    	final LUTRealTransform transform = new LUTRealTransform( lut, nMatrixDimensions, nMatrixDimensions );
    	
   
		final ArrayList<double[]> fitList = new ArrayList< double[] >();
		for ( int i = 0; i < lut.length; ++i ) {
			fitList.add( new double[ options.comparisonRange ] );
		}
		
		final ListImg< double[] > localFits = new ListImg<double[]>( fitList, fitList.size() );
    	
		LocalizedCorrelationFit.estimateFromMatrix( matrix, lut, weights, multipliers, transform, options.comparisonRange, correlationFitModel, categorizer, localFits ); // this has window range
		
		EstimateQualityOfSlice.estimateQuadraticFromMatrix( matrix, 
				weights, 
				multipliers, 
				lut, 
				localFits, 
				options.multiplierGenerationRegularizerWeight, 
				options.comparisonRange, 
				options.multiplierEstimationIterations );
		
		
		for ( int i = 0; i < multipliers.length; ++i ) {
			final double diff = 1.0 - multipliers[ i ];
			weights[ i ] = Math.exp( -0.5*diff*diff / ( options.multiplierWeightsSigma ) );
		}
		
		final TreeMap< Long, ArrayList< ConstantPair< Double, Double > > > shifts =
		            ShiftCoordinates.collectShiftsFromMatrix(
		                    lut,
		                    matrix,
		                    weights,
		                    multipliers,
		                    localFits );
		
		final double[] mediatedShifts = new double[ lut.length ];
		this.shiftMediator.mediate( shifts, mediatedShifts );
		
		return mediatedShifts;
    }
        
        
    public void applyShifts( 
    		final double[] coordinates, 
    		final double[] shifts, 
    		final double[] multipliers,
    		final PermutationTransform permutation, 
    		final Options options ) 
    {
        	
    	final ArrayImg<DoubleType, DoubleArray> coordinateImage = ArrayImgs.doubles( coordinates, coordinates.length );
    	final IntervalView<DoubleType> transformed              = Views.interval( new TransformView< DoubleType >( coordinateImage, permutation ), coordinateImage );
    	final double inverseCoordinateUpdateRegularizerWeight   = 1 - options.coordinateUpdateRegularizerWeight;
    	
    
		
		final double[] smoothedShifts = new double[ shifts.length ];
		final double[] gaussKernel    = new double[ options.shiftsSmoothingRange + 1 ];
		gaussKernel[0] = 1.0;
		double normalizingConstant = gaussKernel[0];
		for ( int i = 1; i < gaussKernel.length; ++i ) {
			gaussKernel[ i ] = Math.exp( -0.5 * i * i / ( options.shiftsSmoothingSigma * options.shiftsSmoothingSigma ) );
			normalizingConstant += 2* gaussKernel[ i ];
		}
		
		for (int i = 0; i < gaussKernel.length; i++) {
			gaussKernel[ i ] /= normalizingConstant;
		}
		
		final OutOfBounds<DoubleType> mediatedRA = Views.extendMirrorSingle( ArrayImgs.doubles( shifts, shifts.length ) ).randomAccess();
		final OutOfBounds<DoubleType> weightsRA  = Views.extendMirrorSingle( ArrayImgs.doubles( multipliers, multipliers.length ) ).randomAccess();
		for (int i = 0; i < smoothedShifts.length; i++) {
			smoothedShifts[ i ] = 0.0;
			double weightSum = 0.0;
			for ( int k = -options.shiftsSmoothingRange; k <= options.shiftsSmoothingRange; ++k ) {
				mediatedRA.setPosition( i + k, 0 );
				weightsRA.setPosition( mediatedRA );
				final double w = gaussKernel[ Math.abs( k ) ] * weightsRA.get().get();
				final double val = mediatedRA.get().get() * w;
				smoothedShifts[ i ] += val;
				weightSum += w;
			}
			smoothedShifts[ i ] /= weightSum;
		}
	    
		final double accumulatedCorrections = 0.0;
		final Cursor<DoubleType> c = transformed.cursor();
		for ( int ijk = 0; c.hasNext(); ++ijk ) {
			final DoubleType valObject = c.next();
			double val = valObject.get();
			final double prev = val;
		    val += accumulatedCorrections + options.shiftProportion * smoothedShifts[ ijk ];
		    val = options.coordinateUpdateRegularizerWeight * prev + inverseCoordinateUpdateRegularizerWeight * val;
		    valObject.set( val );
		}
    }
    
    
    public void preventReorder( 
    		final double[] coordinates, 
    		final PermutationTransform permutation,
    		final Options options ) {
    	final ArrayImg<DoubleType, DoubleArray> coordinateImage = ArrayImgs.doubles( coordinates, coordinates.length );
    	final IntervalView<DoubleType> permuted = Views.interval( new TransformView< DoubleType >( coordinateImage, permutation ), coordinateImage );
    	final Cursor<DoubleType> c = permuted.cursor();
    	double previous = c.next().getRealDouble();
    	while( c.hasNext() ) {
    		final DoubleType currentObject = c.next();
    		double currentVal        = currentObject.get();
    		if ( previous > currentVal )
    			currentVal = previous + options.minimumSectionThickness;
    		previous = currentVal;
    		currentObject.set( currentVal );
    	}
    }
			
			
    public void regularize( 
		final double[] coordinates,
		final int[] inversePermutationLut,
		final Options options ) 
    {
		final float[] floatLut = new float[ 2 ]; final float[] arange = new float[ 2 ];
		final float[] floatWeights = new float[ 2 ];
		for (int i = 0; i < arange.length; i++) {
			arange[i] = i;
			floatWeights[i] = 1.0f;
		}
		final int maxIndex = inversePermutationLut.length - 1;
		floatLut[ 0 ]       = (float) coordinates[ inversePermutationLut[ 0 ] ];
		arange[ 0 ]         = 0f;
		floatWeights[ 0 ]   = 1.0f;
		floatLut[ 1 ]       = (float) coordinates[ inversePermutationLut[ maxIndex ] ];
		arange[ 1 ]         = maxIndex;
		floatWeights[ 1 ] = 1.0f;
		
		final AffineModel1D coordinatesFitModel = new AffineModel1D();
		
		
		try {
			coordinatesFitModel.fit( new float[][]{floatLut}, new float[][]{arange}, floatWeights );
			final double[] affineArray = new double[ 2 ];
			coordinatesFitModel.toArray( affineArray );
			
			for ( int i = 0; i < coordinates.length; ++i ) {
				coordinates[i] = affineArray[0] * coordinates[i] + affineArray[1];
			}
		} catch (final NotEnoughDataPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final IllDefinedDataPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
}

