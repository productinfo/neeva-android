#!/bin/sh

# Can also specify "debug" on the command line.
mode=${1:-"release"}

root_dir="$(dirname $0)/.."
scripts_dir="$root_dir/scripts"

if [ $mode = "release" ]; then
    app_apk_name="app-release-unsigned"
else
    app_apk_name="app-$mode"
fi
app_apk="$root_dir/app/build/outputs/apk/$mode/$app_apk_name.apk"
weblayer_support_apk="$root_dir/weblayer_support/build/outputs/apk/$mode/weblayer_support-$mode.apk"

snapshots_dir="$root_dir/weblayer_support/snapshots"
chromium_release_dir="$snapshots_dir/$(cat $snapshots_dir/CURRENT_VERSION)-arm"
out_dir="$root_dir/build/$mode"
out_dir_abs_path="$(pwd)/$out_dir"

out_bundle="$out_dir/neeva.aab"
out_apks="$out_dir/neeva.apks"

rm -f $out_bundle $out_apks
mkdir -p $out_dir

make_archive_if_necessary() {
    src_apk=$1
    archive=$2

    if [ $src_apk -nt "$archive.zip" ]; then
        $scripts_dir/make-archive.sh $src_apk $archive
    else
        echo "Using cached $(basename $archive).zip"
    fi
}

make_archive_if_necessary $app_apk $out_dir/base
make_archive_if_necessary $weblayer_support_apk $out_dir/weblayer_support
make_archive_if_necessary $chromium_release_dir/WebLayerSupport.apk $out_dir/weblayer_support_impl

# Copy generated manifest over top the one from Chromium:
tmpdir=$(mktemp -d -t $(basename $0)) || exit 1
unzip $out_dir/weblayer_support.zip manifest/AndroidManifest.xml -d $tmpdir > /dev/null
(cd $tmpdir && zip -r $out_dir_abs_path/weblayer_support_impl.zip . > /dev/null)

echo "Building build/$mode/neeva.aab..."
$scripts_dir/bundletool.sh build-bundle \
    --modules=$out_dir/base.zip,$out_dir/weblayer_support_impl.zip \
    --output=$out_bundle \
    --config=$scripts_dir/bundleconfig.json

echo "Building build/$mode/neeva.apks..."
$scripts_dir/bundletool.sh build-apks --bundle=$out_bundle --output=$out_apks --overwrite

echo "Done"