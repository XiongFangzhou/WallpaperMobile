package dev.workshopvideo.core;
/** One normalized RGB matrix for both Canvas and GLES (bias is normalized 0..1). */
public final class PictureColors {
 public static float[] matrix(float brightness,float contrast,float saturation,float hue){
  float angle=(float)Math.toRadians(hue),co=(float)Math.cos(angle),si=(float)Math.sin(angle);float lr=.2126f,lg=.7152f,lb=.0722f;
  float[] h={lr+co*(1-lr)-si*lr,lg-co*lg-si*lg,lb-co*lb+si*(1-lb),lr-co*lr+si*.143f,lg+co*(1-lg)+si*.140f,lb-co*lb-si*.283f,lr-co*lr-si*(1-lr),lg-co*lg+si*lg,lb+co*(1-lb)+si*lb};
  float[] out=new float[12];float[] lum={lr,lg,lb};float gain=brightness*contrast;
  for(int r=0;r<3;r++){for(int c=0;c<3;c++){float v=0;for(int k=0;k<3;k++)v+=((1-saturation)*lum[k]+(r==k?saturation:0))*h[k*3+c];out[r*4+c]=v*gain;}out[r*4+3]=brightness*(.5f-.5f*contrast);}
  return out;
 }
 private PictureColors(){}
}
