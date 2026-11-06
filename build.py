"""Standalone build. No account, original game binary, or personal save needed."""
import argparse,pathlib,subprocess,zipfile
ROOT=pathlib.Path(__file__).resolve().parent

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sdk',type=pathlib.Path,required=True);p.add_argument('--jdk',type=pathlib.Path,required=True)
    p.add_argument('--keystore',type=pathlib.Path);p.add_argument('--password-file',type=pathlib.Path)
    a=p.parse_args();out=ROOT/'build';out.mkdir(exist_ok=True)
    java=a.jdk/'bin';suffix='.exe' if (java/'java.exe').exists() else ''
    platform=a.sdk/'platforms/android-35/android.jar'
    if not platform.exists():platform=next(a.sdk.rglob('android.jar'))
    bt=a.sdk/'build-tools/35.0.0'
    if not bt.exists():bt=next(a.sdk.rglob('aapt2'+suffix)).parent
    def run(*cmd):subprocess.run([str(x) for x in cmd],check=True)
    classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True)
    run(java/('javac'+suffix),'-encoding','UTF-8','-source','8','-target','8','-classpath',platform,'-d',classes,*sorted((ROOT/'src').rglob('*.java')))
    run(java/('jar'+suffix),'cf',out/'classes.jar','-C',classes,'.')
    run(java/('java'+suffix),'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',platform,'--output',dex,out/'classes.jar')
    run(bt/('aapt2'+suffix),'link','-I',platform,'--manifest',ROOT/'AndroidManifest.xml','-A',ROOT/'assets','-o',out/'unsigned.apk')
    with zipfile.ZipFile(out/'unsigned.apk','a') as z:z.write(dex/'classes.dex','classes.dex')
    run(bt/('zipalign'+suffix),'-f','4',out/'unsigned.apk',out/'aligned-unsigned.apk')
    if a.keystore:
        if not a.password_file:p.error('--password-file is required with --keystore')
        target=out/'lostword-save-extractor.apk'
        run(java/('java'+suffix),'-jar',bt/'lib/apksigner.jar','sign','--ks',a.keystore,'--ks-pass','file:'+str(a.password_file),'--out',target,out/'aligned-unsigned.apk')
        run(java/('java'+suffix),'-jar',bt/'lib/apksigner.jar','verify','--verbose',target)
    print('Build complete:',out)

if __name__=='__main__':main()
