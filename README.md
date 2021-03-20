[![](https://travis-ci.org/saalfeldlab/z-spacing.svg?branch=master)](https://travis-ci.org/saalfeldlab/z-spacing)
[![](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/saalfeldlab/z-spacing?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# Z-Spacing Correction

Estimate the positions and spacing between sections (or at local points) of three dimensional image data. This method may be applied to any imaging modality that acquires 3-dimensional data as a stack of 2-dimensional sections. We provide plugins for both Fiji and TrakEM2.

## Citation
Please note that the z-spacing correction plugin available through Fiji, is based on a publication. If you use it successfully for your research please cite our work:

P. Hanslovsky, J. Bogovic, S. Saalfeld (2015) Post-acquisition image based compensation for thickness variation in microscopy section series, In ''International Symposium on Biomedical Imaging (ISBI'15)'', New York [http://arxiv.org/abs/1411.6970]

P. Hanslovsky, J. Bogovic, S. Saalfeld (2017) Image-based correction of continuous and discontinuous non-planar axial distortion in serial section microscopy, _Bioinformatics_ **33**(9), 1379–1386 [https://academic.oup.com/bioinformatics/article/33/9/1379/2736362]

## Introduction
Serial section Microscopy, using either optical or physical sectioning, is an established method for volumetric anatomy reconstruction.  Section series imaged with Electron Microscopy are currently vital for the reconstruction of the synaptic connectivity of entire animal brains such as that of *Drosophila melanogaster*.  The process of removing ultrathin layers from a solid block containing the specimen, however, is a fragile procedure and has limited precision with respect to section thickness.  Optical sectioning techniques often suffer from increasing distortion as sections deeper inside the tissue are imaged.  On summary, section thickness that is supposed to be constant, in practice is not and has to be corrected where precise measurement is desired.  We have developed a method to estimate the relative *z*-position of each individual section as a function of signal change across the section series.  The [Fiji](http://fiji.sc) plugin **Transform** > **Z-Spacing Correction** and the [TrakEM2](http://fiji.sc/TrakEM2) plugin **Plugins** > **LayerZPosition** implement this method.

## Parameters
<dl>
  <dt>Neighborhood range</dt>
  <dd>Specifies the neighborhood around each section for which pairwise similarities are calculated.</dd>
  <dt>Outer iterations</dt>
  <dd>Specifies the number of iterations in the outer loop of the optimizer.</dd>
  <dt>Outer regularization</dt>
  <dd>Specifies the amount of regularization in the outer loop of the optimizer. 0 means no regularization, 1 means full regularization (no change).  The regularizer in the outer loop damps the updates during each iteration by the specified fraction.</dd>
  <dt>Inner Iterations</dt>
  <dd>Specifies the number of iterations in inner loops of the optimizer.</dd>
  <dt>Inner Regularization</dt>
  <dd>Specifies the amount of regularization in the outer loop of the optimizer. 0 means no regularization, 1 means full regularization (no change).  The per-section quality weight requires regularization to avoid trivial solutions.  We use a Tikhonov regularizer towards 1.0 weight.</dd>
  <dt>Allow reordering</dt>
  <dd>Specifies whether layers/ sections can change their relative order in the series.</dd>
</dl>

## Visitors
*z*-spacing uses the [Visitor](https://github.com/saalfeldlab/z-spacing/blob/master/src/main/java/org/janelia/thickness/inference/visitor/Visitor.java) interface to inspect the state of each iteration of the inference. Currently, two visitors are provided for use in the **Z-Spacing Correction** Fiji plugin:
<dl>
 <dt>lazy (default)</dt>
 <dd>Do not inspect state of inference.</dd>
 <dt>variables</dt>
 <dd>Log a user specified selection of
 <ul>
 <li>the current (local) function estimate ("&lt;root&gt;/correlation-fit/%0nd.csv"),</li>
 <li>scaling factors ("&lt;root&gt;/scaling-factors/%0nd.csv"),</li>
 <li>coordinate transform/look-up table ("&lt;root&gt;/lut/%0nd.csv"), and</li>
 <li>scaled and warped matrix ("&lt;root&gt;/matrices/%0nd.csv"),</li>
 </ul>
 where the &lt;root&gt; directory is specified by the user, and n is the minimum number of digits necessary for displaying the specified number of iterations.
 </dd>
</dl>
### Adding Visitors
Users can add their own visitors by calling one of
```java
org.janelia.thickness.plugin.ZPositionCorrection.addVisitor( final String name, final Visitor visitor )
org.janelia.thickness.plugin.ZPositionCorrection.addVisitor( final String name, final VisitorFactory factory )
```
from the script editor or the beanshell interpreter. The first option is the go-to choice for simple visitors. If the visitor needs specific information from the input matrix or options, the second option should be used. To that end, the user needs to implement the [VisitorFactory](https://github.com/saalfeldlab/z-spacing/blob/master/src/main/java/org/janelia/thickness/plugin/ZPositionCorrection.java#L138) interface. The following simple examples demonstrate how to add a visitor using both methods and can be easily extended for more specific tasks:

- Simple Visitor
```java
import ij.IJ;
import org.janelia.thickness.inference.visitor.Visitor;
import org.janelia.thickness.plugin.ZPositionCorrection;

ZPositionCorrection.addVisitor( "yay", new Visitor()
	{ 
		act( iteration, matrix, scaledMatrix, lut, permutation, inversePermutation, multipliers, estimatedFit ) {
		IJ.log( "Doing iteration " + iteration );
		}
	}
);
```
- Using Factory
```java
import ij.IJ;
import org.janelia.thickness.inference.visitor.Visitor;
import org.janelia.thickness.plugin.ZPositionCorrection;

factory = new ZPositionCorrection.VisitorFactory() {
	public Visitor create( matrix, options ) { 
		return new Visitor() {
			act( iteration, matrix, scaledMatrix, lut, permutation, inversePermutation, multipliers, estimatedFit )
			{
				IJ.log( "Doing iteration " + iteration + "/" + options.nIterations );
			}
		};
	}
};

ZPositionCorrection.addVisitor( "yay2", factory );
```

Note that manually added visitors will not be stored, i.e. they will be lost after re-starting Fiji, and the "lazy" visitor cannot be overwritten.
