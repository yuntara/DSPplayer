#include <jni.h>
#include <string>
#include <math.h>
#define SIZEX 65536


extern "C" {

jint n,m;
jdouble filR1_r[SIZEX+1];
jdouble filR2_r[SIZEX+1];
jdouble filL1_r[SIZEX+1];
jdouble filL2_r[SIZEX+1];
jdouble filR1_i[SIZEX+1];
jdouble filR2_i[SIZEX+1];
jdouble filL1_i[SIZEX+1];
jdouble filL2_i[SIZEX+1];
jdouble c_r;// [SIZEX*2];
jdouble c_i;// [SIZEX*2];
jdouble d_r;// [SIZEX*2];
jdouble d_i;// [SIZEX*2];
jdouble _cos[SIZEX], _sin[SIZEX];
jstring
Java_net_yuntara_dspplayer_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";

    return env->NewStringUTF(hello.c_str());
}

void
cfft(
        double *x, double *y,jboolean isReverse) {


    int i, j, k, n1, n2, a;
    jdouble c, s, t1, t2;

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

    // FFT
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
    for (int i = 0; i < n; i++) {
        x[i] /= n;
        y[i] /= n;
    }
   }

}
void cifft(double *x, double *y){
    cfft(x,y,true);
}
void Java_net_yuntara_dspplayer_StreamPlayer_ccomboluteRL(JNIEnv *env, jobject jthis, jdoubleArray jx, jdoubleArray jy){
    jdouble *x = env->GetDoubleArrayElements(jx, NULL);
    jdouble *y = env->GetDoubleArrayElements(jy, NULL);
    jdouble buf;
    jdouble b_r,b_i;

    cfft(x,y,false);
/*
 RFFT_r= (x[j] + x[n-j])/2
 RFFT_i= (y[j] - y[n-j])/2
 LFFT_r= (x[j] - x[n-j])/2
 LFFT_i= (y[j] + y[n-j])/2

 */
    y[SIZEX] =0;
    for (int j = 0; j < SIZEX + 1; j++) {
        if (j == 0) {
            c_r = (x[0]);
            c_i = 0;
            d_r = (y[0]);
            d_i = 0;
        } else {
            c_r = (x[j] + x[n - j]) / 2;
            c_i = (y[j] - y[n - j]) / 2;

            d_r = (x[j] - x[n - j]) / 2;
            d_i = (y[j] + y[n - j]) / 2;
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
        b_i += (d_r* filL2_i[j]) + (d_i * filL2_r[j]);
        x[j] -=  b_i;
        y[j] +=  b_r;
        if (j > 0 && j < SIZEX) {
            x[SIZEX*2-j] += b_i;
            y[SIZEX*2-j] += b_r;
        }



    }


    cifft(x,y);

    env->ReleaseDoubleArrayElements(jx,x,NULL);
    env->ReleaseDoubleArrayElements(jy,y,NULL);
}

void Java_net_yuntara_dspplayer_StreamPlayer_csetfil(JNIEnv *env, jobject jthis,
                                                     jdoubleArray jr1r, jdoubleArray jr1i,jdoubleArray jr2r,jdoubleArray jr2i,
                                                     jdoubleArray jl1r, jdoubleArray jl1i,jdoubleArray jl2r,jdoubleArray jl2i
){
    jdouble *r1r = env->GetDoubleArrayElements(jr1r,NULL);
    for(int i=0;i<=SIZEX;i++)filR1_r[i] = r1r[i]*100;
    env->ReleaseDoubleArrayElements(jr1r,r1r,NULL);

    jdouble *r1i = env->GetDoubleArrayElements(jr1i,NULL);
    for(int i=0;i<=SIZEX;i++)filR1_i[i] = r1i[i]*100;
    env->ReleaseDoubleArrayElements(jr1i,r1i,NULL);

    jdouble *r2r = env->GetDoubleArrayElements(jr2r,NULL);
    for(int i=0;i<=SIZEX;i++)filR2_r[i] = r2r[i]*100;
    env->ReleaseDoubleArrayElements(jr2r,r2r,NULL);

    jdouble *r2i = env->GetDoubleArrayElements(jr2i,NULL);
    for(int i=0;i<=SIZEX;i++)filR2_i[i] = r2i[i]*100;
    env->ReleaseDoubleArrayElements(jr2i,r2i,NULL);

    jdouble *l1r = env->GetDoubleArrayElements(jl1r,NULL);
    for(int i=0;i<=SIZEX;i++)filL1_r[i] = l1r[i]*100;
    env->ReleaseDoubleArrayElements(jl1r,l1r,NULL);

    jdouble *l1i = env->GetDoubleArrayElements(jl1i,NULL);
    for(int i=0;i<=SIZEX;i++)filL1_i[i] = l1i[i]*100;
    env->ReleaseDoubleArrayElements(jl1i,l1i,NULL);

    jdouble *l2r = env->GetDoubleArrayElements(jl2r,NULL);
    for(int i=0;i<=SIZEX;i++)filL2_r[i] = l2r[i]*100;
    env->ReleaseDoubleArrayElements(jl2r,l2r,NULL);

    jdouble *l2i = env->GetDoubleArrayElements(jl2i,NULL);
    for(int i=0;i<=SIZEX;i++)filL2_i[i] = l2i[i]*100;
    env->ReleaseDoubleArrayElements(jl2i,l2i,NULL);


}
void Java_net_yuntara_dspplayer_StreamPlayer_cfftinit(JNIEnv *env, jobject jthis, jint size) {
    n = size;
    m = (int) (log(n) / log(2));


    // precompute tables
    //cos = new double[n / 2];
    //sin = new double[n / 2];

    for (int i = 0; i < n / 2; i++) {
        _cos[i] = cos(-2 * M_PI * i / n);
        _sin[i] = sin(-2 * M_PI * i / n);
    }

}

}
