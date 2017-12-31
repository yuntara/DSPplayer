#include <jni.h>
#include <string>
#include <math.h>

#define SIZEX (65536/2)
//#define  FLOAT float
//#define JDOUBLE JDOUBLE

#define COS cos
#define SIN sin
typedef float FLOAT;
typedef jdouble JDOUBLE;
typedef jdoubleArray JDOUBLEArray;


extern "C" {

jint n,m;
double filR1_r[SIZEX+1];
double filR2_r[SIZEX+1];
double filL1_r[SIZEX+1];
double filL2_r[SIZEX+1];
double filR1_i[SIZEX+1];
double filR2_i[SIZEX+1];
double filL1_i[SIZEX+1];
double filL2_i[SIZEX+1];
double c_r;// [SIZEX*2];
double c_i;// [SIZEX*2];
double d_r;// [SIZEX*2];
double d_i;// [SIZEX*2];
double _cos[SIZEX], _sin[SIZEX];

jstring
Java_net_yuntara_dspplayer_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
  
    return env->NewStringUTF(hello.c_str());
}

void cfft(JDOUBLE *x, JDOUBLE *y,bool isReverse) {


    int i, j, k, n1, n2, a;
    double c, s, t1, t2;

    // Bit-reverse
    j = 0;
    n2 = n / 2;
    for (i = 1; i < n - 1; i++) {
        n1 = n2;
        while (j >= n1) {
            j = j - n1;
            n1 = n1 / 2;
        }
        j = j + n1;

        if (i < j) {
            t1 = x[i];
            x[i] = x[j];
            x[j] = t1;
            t1 = y[i];
            y[i] = y[j];
            y[j] = t1;
        }
    }


    n1 = 0;
    n2 = 1;

    for (i = 0; i < m; i++) {
        n1 = n2;
        n2 = n2 + n2;
        a = 0;

        for (j = 0; j < n1; j++) {
            if (isReverse) {
                c = _cos[a];
                s = -_sin[a];
            } else {
                c = _cos[a];
                s = _sin[a];
            }
            a += 1 << (m - i - 1);

            for (k = j; k < n; k = k + n2) {
                t1 = c * x[k + n1] - s * y[k + n1];
                t2 = s * x[k + n1] + c * y[k + n1];
                x[k + n1] = x[k] - t1;
                y[k + n1] = y[k] - t2;
                x[k] = x[k] + t1;
                y[k] = y[k] + t2;
            }
        }

    }
    if (isReverse) {
        for (int q = 0; q < n; q++) {
            y[q] /= n;
            x[q] /= n;
        }
    }

}
void Java_net_yuntara_dspplayer_StreamPlayer_ccomboluteRL(JNIEnv *env, jobject /* jthis */, JDOUBLEArray jx, JDOUBLEArray jy){
    JDOUBLE *x = env->GetDoubleArrayElements(jx, NULL);
    JDOUBLE *y = env->GetDoubleArrayElements(jy, NULL);
    JDOUBLE buf;
    double b_r,b_i;
    JDOUBLE xi[SIZEX*2+1],yi[SIZEX*2+1];
    for(int i=0;i<SIZEX*2+1;i++){
        xi[i]=0.0;
        yi[i]=0.0;
    }
    cfft(x,xi,false);
    cfft(y,yi,false);
/*
 R_FFT_r is (x[j] + x[n-j])/2
 R_FFT_i is (y[j] - y[n-j])/2
 L_FFT_r is (x[j] - x[n-j])/2
 L_FFT_i is (y[j] + y[n-j])/2

 */
    //y[SIZEX] =0;
    /*
     *
     */
    for (int j = 0; j < SIZEX + 1; j++) {
        if(j == SIZEX){
            x[SIZEX] = 0;
            y[SIZEX] = 0;
            continue;
        }
        if (j == 0) {
            c_r = (x[0]);
            c_i = 0.0f;
            d_r = (y[0]);
            d_i = 0.0f;
        } else {
            c_r = x[j];
            c_i = xi[j];

            d_r = y[j];
            d_i = yi[j];
        }
        /*
         x     +yi        = c + d i;
         -x[n-j]+y[n-j] i = -c + d i;
         d = x-x
         c_r = x+x[n-j]/2
         c_i = y-yn/2

         c_r+c_ =
         */
        //c_r = (c_r+d_r)/2;
        //c_i = (c_i+d_i)/2;
/*
                R1 OK migi
                R2 hidari
                L1 hidari
                L2 migi
                 */
        //d_r = c_r;
        //d_i = c_i;
        b_r = (c_r* filR2_r[j]) - (c_i * filR2_i[j]);
        b_i = (c_r * filR2_i[j]) + (c_i * filR2_r[j]);
        b_r += (d_r * filL1_r[j]) - (d_i * filL1_i[j]);
        b_i += (d_r * filL1_i[j]) + (d_i * filL1_r[j]);
        x[j] = b_r;
        y[j] = b_i;
        if (j > 0 && j < SIZEX) {
            x[SIZEX * 2 - j] = b_r;
            y[SIZEX * 2 - j] = -b_i;
        }
        //right
        b_r = (c_r * filR1_r[j]) - (c_i * filR1_i[j]);
        b_i = (c_r * filR1_i[j]) + (c_i * filR1_r[j]);

        b_r += (d_r * filL2_r[j]) - (d_i * filL2_i[j]);
        b_i += (d_r* filL2_i[j])  + (d_i * filL2_r[j]);

        x[j] -=  b_i;
        y[j] +=  b_r;
        if (j > 0 && j < SIZEX) {
            x[SIZEX*2-j] += b_i;
            y[SIZEX*2-j] += b_r;
        }


    }

    //ifft
    cfft(x,y,true);

    env->ReleaseDoubleArrayElements(jx,x,NULL);
    env->ReleaseDoubleArrayElements(jy,y,NULL);
}
void Java_net_yuntara_dspplayer_StreamPlayer_ccomboluteRL2(JNIEnv *env, jobject /* jthis */, JDOUBLEArray jx, JDOUBLEArray jy){
    JDOUBLE *x = env->GetDoubleArrayElements(jx, NULL);
    JDOUBLE *y = env->GetDoubleArrayElements(jy, NULL);
    JDOUBLE buf;
    double b_r,b_i;

    cfft(x,y,false);
/*
 R_FFT_r is (x[j] + x[n-j])/2
 R_FFT_i is (y[j] - y[n-j])/2
 L_FFT_r is (x[j] - x[n-j])/2
 L_FFT_i is (y[j] + y[n-j])/2

 */
    //y[SIZEX] =0;
    /*
     *
     */
    for (int j = 0; j < SIZEX + 1; j++) {
        if(j == SIZEX){
            x[SIZEX] = 0;
            y[SIZEX] = 0;
            continue;
        }
        if (j == 0) {
            d_r = (x[0]);
            d_i = 0.0f;
            c_r = (y[0]);
            c_i = 0.0f;
        } else {
            d_r = (x[j] + x[n - j]) / 2.0f;
            d_i = (y[j] - y[n - j]) / 2.0f;

            c_r = (x[j] - x[n - j]) / 2.0f;
            c_i = (y[j] + y[n - j]) / 2.0f;
        }
        /*
         x     +yi        = c + d i;
         -x[n-j]+y[n-j] i = -c + d i;
         d = x-x
         c_r = x+x[n-j]/2
         c_i = y-yn/2

         c_r+c_ =
         */
        //c_r = (c_r+d_r)/2;
        //c_i = (c_i+d_i)/2;
/*
                R1 OK migi
                R2 hidari
                L1 hidari
                L2 migi
                 */
        //d_r = c_r;
        //d_i = c_i;
        b_r = (c_r* filR2_r[j]) - (c_i * filR2_i[j]);
        b_i = (c_r * filR2_i[j]) + (c_i * filR2_r[j]);
        b_r += (d_r * filL1_r[j]) - (d_i * filL1_i[j]);
        b_i += (d_r * filL1_i[j]) + (d_i * filL1_r[j]);
        x[j] = b_r;
        y[j] = b_i;
        if (j > 0 && j < SIZEX) {
            x[SIZEX * 2 - j] = b_r;
            y[SIZEX * 2 - j] = -b_i;
        }
        //right
        b_r = (c_r * filR1_r[j]) - (c_i * filR1_i[j]);
        b_i = (c_r * filR1_i[j]) + (c_i * filR1_r[j]);

        b_r += (d_r * filL2_r[j]) - (d_i * filL2_i[j]);
        b_i += (d_r* filL2_i[j])  + (d_i * filL2_r[j]);

        x[j] -=  b_i;
        y[j] +=  b_r;
        if (j > 0 && j < SIZEX) {
            x[SIZEX*2-j] += b_i;
            y[SIZEX*2-j] += b_r;
        }


    }

    //ifft
    cfft(x,y,true);

    env->ReleaseDoubleArrayElements(jx,x,NULL);
    env->ReleaseDoubleArrayElements(jy,y,NULL);
}

void Java_net_yuntara_dspplayer_StreamPlayer_csetfil(JNIEnv *env, jobject /*jthis*/,
                                                     JDOUBLEArray jr1r, JDOUBLEArray jr1i,JDOUBLEArray jr2r,JDOUBLEArray jr2i,
                                                     JDOUBLEArray jl1r, JDOUBLEArray jl1i,JDOUBLEArray jl2r,JDOUBLEArray jl2i
){
    const double vol = 0.004;
    JDOUBLE *r1r = env->GetDoubleArrayElements(jr1r,NULL);
    for(int i=0;i<=SIZEX;i++)filR1_r[i] = r1r[i]*vol;
    env->ReleaseDoubleArrayElements(jr1r,r1r,NULL);

    JDOUBLE *r1i = env->GetDoubleArrayElements(jr1i,NULL);
    for(int i=0;i<=SIZEX;i++)filR1_i[i] = r1i[i]*vol;
    env->ReleaseDoubleArrayElements(jr1i,r1i,NULL);

    JDOUBLE *r2r = env->GetDoubleArrayElements(jr2r,NULL);
    for(int i=0;i<=SIZEX;i++)filR2_r[i] = r2r[i]*vol;
    env->ReleaseDoubleArrayElements(jr2r,r2r,NULL);

    JDOUBLE *r2i = env->GetDoubleArrayElements(jr2i,NULL);
    for(int i=0;i<=SIZEX;i++)filR2_i[i] = r2i[i]*vol;
    env->ReleaseDoubleArrayElements(jr2i,r2i,NULL);

    JDOUBLE *l1r = env->GetDoubleArrayElements(jl1r,NULL);
    for(int i=0;i<=SIZEX;i++)filL1_r[i] = l1r[i]*vol;
    env->ReleaseDoubleArrayElements(jl1r,l1r,NULL);

    JDOUBLE *l1i = env->GetDoubleArrayElements(jl1i,NULL);
    for(int i=0;i<=SIZEX;i++)filL1_i[i] = l1i[i]*vol;
    env->ReleaseDoubleArrayElements(jl1i,l1i,NULL);

    JDOUBLE *l2r = env->GetDoubleArrayElements(jl2r,NULL);
    for(int i=0;i<=SIZEX;i++)filL2_r[i] = l2r[i]*vol;
    env->ReleaseDoubleArrayElements(jl2r,l2r,NULL);

    JDOUBLE *l2i = env->GetDoubleArrayElements(jl2i,NULL);
    for(int i=0;i<=SIZEX;i++)filL2_i[i] = l2i[i]*vol;
    env->ReleaseDoubleArrayElements(jl2i,l2i,NULL);


}
void Java_net_yuntara_dspplayer_StreamPlayer_cfftinit(JNIEnv *env, jobject jthis, jint size) {
    n = size;
    m = static_cast<int>( log(n) / log(2));

    // precompute tables

    for (int i = 0; i < n / 2; i++) {

        _cos[i] = static_cast<JDOUBLE>( COS((-2.0 * (double)M_PI * i )/ n) );
        _sin[i] = static_cast<JDOUBLE>( SIN((-2.0 * (double)M_PI * i )/ n) );
    }

}

}
