package org.janelia.thickness.lut;

import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class LUTGrid extends AbstractLUTGrid {

	public LUTGrid(
			final int numSourceDimensions, 
			final int numTargetDimensions,
			final RandomAccessibleInterval< DoubleType > lutArray) {
		super(numSourceDimensions, 
				numTargetDimensions, 
				lutArray);
	}

	@Override
	public void apply(final double[] source, final double[] target) {
		this.updateCoordinates( source );
		for ( int d = 0; d < this.nNonTransformedCoordinates; ++d ) {
			target[d] = source[d];
//			IJ.log( String.format( "1SAME:\t %d: %f ~> %f", d, source[d], target[d] ) );
		}
		for ( int d = this.nNonTransformedCoordinates; d < target.length; ++d) {
			target[d] = this.applyChecked( source[d] );
//			IJ.log( String.format( "1MODIFED:\t %d: %f ~> %f", d, source[d], target[d] ) );
		}
	}

	@Override
	public void apply(final float[] source, final float[] target) {
		this.updateCoordinates( source );
		for ( int d = 0; d < this.nNonTransformedCoordinates; ++d ) {
			target[d] = source[d];
//			IJ.log( String.format( "2SAME:\t %d: %f ~> %f", d, source[d], target[d] ) );
		}
		for ( int d = this.nNonTransformedCoordinates; d < target.length; ++d) {
			target[d] = (float) this.applyChecked( source[d] );
//			IJ.log( String.format( "2MODIFED:\t %d: %f ~> %f", d, source[d], target[d] ) );
		}
	}
	
	@Override
	public void apply(final RealLocalizable source, final RealPositionable target) {
		this.updateCoordinates( source );
		for ( int d = 0; d < this.nNonTransformedCoordinates; ++d ) {
			target.setPosition( source.getDoublePosition( d ), d);
//			IJ.log( String.format( "3SAME:\t %d: %f ~> %f", d, source.getDoublePosition(d), source.getDoublePosition(d) ) );
		}
		for ( int d = this.nNonTransformedCoordinates; d < target.numDimensions(); ++d ) {
			target.setPosition( this.applyChecked( source.getDoublePosition( d ) ), d);
//			IJ.log( String.format( "3MODIFED:\t %d: %f ~> %f", d, source.getDoublePosition(d), this.applyInverseChecked( source.getDoublePosition(d) ) ) );
		}
	}

	@Override
	public void applyInverse(final double[] source, final double[] target) {
		this.updateCoordinates( target );
//		String LOG_STRING = "";
		for ( int d = 0; d < this.nNonTransformedCoordinates; ++d ) {
			source[d] = target[d];
		}
		for ( int d = this.nNonTransformedCoordinates; d < target.length; ++d ) {
			source[d] = this.applyInverseChecked( target[d] );
//			if ( (int) target[ 0 ] == 203 )
//				LOG_STRING += String.format( "1MODIFED:\t %d: %f ~> %f", d, target[d], source[d] );
		}
//		if ( (int) target[ 0 ] == 203 )
//			IJ.log( LOG_STRING );
	}

	@Override
	public void applyInverse(final float[] source, final float[] target) {
		this.updateCoordinates( target );
//		String LOG_STRING = "";
		for ( int d = 0; d < this.nNonTransformedCoordinates; ++d ) {
			source[d] = target[d];
		}
		for ( int d = this.nNonTransformedCoordinates; d < target.length; ++d ) {
			source[d] = (float) this.applyInverseChecked( target[d] );
//			if ( (int) target[ 0 ] == 203 )
//				LOG_STRING += String.format( "2MODIFED:\t %d: %f ~> %f", d, target[d], source[d] );
		}
//		if ( (int) target[ 0 ] == 203 )
//			IJ.log( LOG_STRING );
	}

	@Override
	public void applyInverse(final RealPositionable source, final RealLocalizable target) {
		final double epsilon = 1e-10;
		this.updateCoordinates( target );
//		String LOG_STRING = "";
		for ( int d = 0; d < this.nNonTransformedCoordinates; ++d ) {
			final double pos  = target.getDoublePosition( d );
			final double dPos = pos - epsilon;
			source.setPosition( dPos > 0 ? dPos : pos, d );
		}
		for ( int d = this.nNonTransformedCoordinates; d < target.numDimensions(); ++d ) {
			final double pos  = this.applyInverseChecked( target.getDoublePosition( d ) );
			final double dPos = pos - epsilon;
			source.setPosition( dPos > 0 ? dPos : pos, d );
//			if ( (int) target.getDoublePosition( 2 ) == 319 && (int) target.getDoublePosition( 3 ) == 235 )
//				LOG_STRING += String.format( "3MODIFED:\t %d: %f ~> %f", d, target.getDoublePosition(d), this.applyInverseChecked( target.getDoublePosition(d) ) ) + "   ";
		}
//		if ( (int) target.getDoublePosition( 2 ) == 319 && (int) target.getDoublePosition( 3 ) == 235 )
//			IJ.log( LOG_STRING );
//			System.out.println( LOG_STRING );
	}

	@Override
	public InvertibleRealTransform inverse() {
		return new InverseRealTransform( this );
	}

	@Override
	public LUTGrid copy() {
		return new LUTGrid(numSourceDimensions, numTargetDimensions, lutArray);
	}
	
	final static public void main( final String[] args ) {
		
		new ImageJ();
		final ImagePlus imp = new ImagePlus( "http://media.npr.org/images/picture-show-flickr-promo.jpg" );
		for ( int y = imp.getHeight()/2; y < imp.getHeight(); ++y ) {
			for ( int x = imp.getWidth()/2; x < imp.getWidth(); ++x ) {
				imp.getProcessor().setf(x, y, Float.NaN);
			}
		}
		imp.show();
		System.out.println( imp.getHeight()+" "+ imp.getWidth()+ " " + imp.getStack().getSize() );

		final int xyIndices = 5;
		final ArrayImg<FloatType, FloatArray> img4D = ArrayImgs.floats( xyIndices, xyIndices, imp.getWidth(), imp.getHeight() );
		
		final ArrayImg<DoubleType, DoubleArray > lut = ArrayImgs.doubles( xyIndices, xyIndices, Math.max( imp.getWidth(), imp.getHeight() ) );
		final ArrayRandomAccess<DoubleType> lutRA = lut.randomAccess();
		final ArrayRandomAccess<FloatType> ra = img4D.randomAccess();
		ra.setPosition( new int[] { 0,0,0,0});
		for ( int yPrime = 0; yPrime < xyIndices; ++yPrime ) {
			ra.setPosition( yPrime, 1 );
			for ( int xPrime = 0; xPrime < xyIndices; ++xPrime ) {
				ra.setPosition( xPrime, 0 );
				for ( int y = 0; y < imp.getHeight(); ++y ) {
					ra.setPosition( y, 3 );
					for ( int x = 0; x < imp.getWidth(); ++x ) {
						ra.setPosition( x, 2 );
						ra.get().set( imp.getProcessor().getf( x, y ) );
						if ( Math.abs( x - y ) > 5  ) {
							ra.get().set( Float.NaN );
						}
					}
				}
			}
		}
			
		
		for ( int xPrime = 0; xPrime < xyIndices; ++xPrime ) {
			for ( int yPrime = 0; yPrime < xyIndices; ++yPrime ) {
				for ( int z = 0; z < lut.dimension( 2 ); ++z ) {
					lutRA.setPosition( new int[] {xPrime, yPrime, z} );
					lutRA.get().set( z );
				}
			}
				
		}
		
		final LUTGrid lutGrid = new LUTGrid(4, 4, lut);
		
		final RealRandomAccessible<FloatType> source = Views.interpolate( Views.extendValue( img4D, new FloatType( Float.NaN ) ), new NLinearInterpolatorFactory<FloatType>());
		final RealTransformRealRandomAccessible<FloatType, InverseRealTransform> source2 = RealViews.transformReal( source, lutGrid );
		final RealRandomAccessible<FloatType> source3 = Views.interpolate( Views.extendBorder( img4D ), new NLinearInterpolatorFactory<FloatType>());
		final RealTransformRealRandomAccessible<FloatType, InverseRealTransform> source4 = RealViews.transformReal( source3, lutGrid );
		final IntervalView<FloatType> v1 = Views.hyperSlice( Views.hyperSlice(img4D, 1, xyIndices/2), 0, xyIndices/2 );
		final IntervalView<FloatType> v2 = Views.interval( Views.hyperSlice( Views.hyperSlice( Views.raster( source2 ), 1, 0 ), 0 , xyIndices - 5 ), new FinalInterval( v1 ) );
		final IntervalView<FloatType> v3 = Views.interval( Views.hyperSlice( Views.hyperSlice( Views.raster( source4 ), 1, 0 ), 0 , xyIndices - 5 ), new FinalInterval( v1 ) );
		
		ImageJFunctions.show( v1, "hyperSlice" );
		ImageJFunctions.show( v2, "extendNaN");
		ImageJFunctions.show( v3, "extendBorder");
		
		
		
	}
	
	

	

}
