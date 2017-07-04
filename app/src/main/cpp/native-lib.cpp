#include <jni.h>
#include <string>
#include <math.h>

#define SIZEX (65536/2)
//#define  FLOAT float
//#define JFLOAT jfloat

#define COS cosf
#define SIN sinf
typedef float FLOAT;
typedef jfloat JFLOAT;
typedef jfloatArray JFLOATArray;


extern "C" {

jint n,m;
JFLOAT filR1_r[SIZEX+1];
JFLOAT filR2_r[SIZEX+1];
JFLOAT filL1_r[SIZEX+1];
JFLOAT filL2_r[SIZEX+1];
JFLOAT filR1_i[SIZEX+1];
JFLOAT filR2_i[SIZEX+1];
JFLOAT filL1_i[SIZEX+1];
JFLOAT filL2_i[SIZEX+1];
JFLOAT c_r;// [SIZEX*2];
JFLOAT c_i;// [SIZEX*2];
JFLOAT d_r;// [SIZEX*2];
JFLOAT d_i;// [SIZEX*2];
JFLOAT _cos[SIZEX], _sin[SIZEX];

jstring
Java_net_yuntara_dspplayer_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
  
    return env->NewStringUTF(hello.c_str());
}

void cfft(JFLOAT *x, JFLOAT *y,bool isReverse) {


    int i, j, k, n1, n2, a;
    JFLOAT c, s, t1, t2;

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
void Java_net_yuntara_dspplayer_StreamPlayer_ccomboluteRL(JNIEnv *env, jobject /* jthis */, JFLOATArray jx, JFLOATArray jy){
    JFLOAT *x = env->GetFloatArrayElements(jx, NULL);
    JFLOAT *y = env->GetFloatArrayElements(jy, NULL);
    JFLOAT buf;
    JFLOAT b_r,b_i;

    cfft(x,y,false);
/*
 R_FFT_r is (x[j] + x[n-j])/2
 R_FFT_i is (y[j] - y[n-j])/2
 L_FFT_r is (x[j] - x[n-j])/2
 L_FFT_i is (y[j] + y[n-j])/2

 */
    y[SIZEX] =0;
    for (int j = 0; j < SIZEX + 1; j++) {
        if (j == 0) {
            c_r = (x[0]);
            c_i = 0.0f;
            d_r = (y[0]);
            d_i = 0.0f;
        } else {
            c_r = (x[j] + x[n - j]) / 2.0f;
            c_i = (y[j] - y[n - j]) / 2.0f;

            d_r = (x[j] - x[n - j]) / 2.0f;
            d_i = (y[j] + y[n - j]) / 2.0f;
        }

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

    env->ReleaseFloatArrayElements(jx,x,NULL);
    env->ReleaseFloatArrayElements(jy,y,NULL);
}

void Java_net_yuntara_dspplayer_StreamPlayer_csetfil(JNIEnv *env, jobject /*jthis*/,
                                                     JFLOATArray jr1r, JFLOATArray jr1i,JFLOATArray jr2r,JFLOATArray jr2i,
                                                     JFLOATArray jl1r, JFLOATArray jl1i,JFLOATArray jl2r,JFLOATArray jl2i
){
    const float vol = 0.004f;
    JFLOAT *r1r = env->GetFloatArrayElements(jr1r,NULL);
    for(int i=0;i<=SIZEX;i++)filR1_r[i] = r1r[i]*vol;
    env->ReleaseFloatArrayElements(jr1r,r1r,NULL);

    JFLOAT *r1i = env->GetFloatArrayElements(jr1i,NULL);
    for(int i=0;i<=SIZEX;i++)filR1_i[i] = r1i[i]*vol;
    env->ReleaseFloatArrayElements(jr1i,r1i,NULL);

    JFLOAT *r2r = env->GetFloatArrayElements(jr2r,NULL);
    for(int i=0;i<=SIZEX;i++)filR2_r[i] = r2r[i]*vol;
    env->ReleaseFloatArrayElements(jr2r,r2r,NULL);

    JFLOAT *r2i = env->GetFloatArrayElements(jr2i,NULL);
    for(int i=0;i<=SIZEX;i++)filR2_i[i] = r2i[i]*vol;
    env->ReleaseFloatArrayElements(jr2i,r2i,NULL);

    JFLOAT *l1r = env->GetFloatArrayElements(jl1r,NULL);
    for(int i=0;i<=SIZEX;i++)filL1_r[i] = l1r[i]*vol;
    env->ReleaseFloatArrayElements(jl1r,l1r,NULL);

    JFLOAT *l1i = env->GetFloatArrayElements(jl1i,NULL);
    for(int i=0;i<=SIZEX;i++)filL1_i[i] = l1i[i]*vol;
    env->ReleaseFloatArrayElements(jl1i,l1i,NULL);

    JFLOAT *l2r = env->GetFloatArrayElements(jl2r,NULL);
    for(int i=0;i<=SIZEX;i++)filL2_r[i] = l2r[i]*vol;
    env->ReleaseFloatArrayElements(jl2r,l2r,NULL);

    JFLOAT *l2i = env->GetFloatArrayElements(jl2i,NULL);
    for(int i=0;i<=SIZEX;i++)filL2_i[i] = l2i[i]*vol;
    env->ReleaseFloatArrayElements(jl2i,l2i,NULL);


}
void Java_net_yuntara_dspplayer_StreamPlayer_cfftinit(JNIEnv *env, jobject jthis, jint size) {
    n = size;
    m = static_cast<int>( log(n) / log(2));

    // precompute tables

    for (int i = 0; i < n / 2; i++) {

        _cos[i] = static_cast<JFLOAT>( COS(-2.0f * (FLOAT)M_PI * i / n) );
        _sin[i] = static_cast<JFLOAT>( SIN(-2.0f * (FLOAT)M_PI * i / n) );
    }

}

}
