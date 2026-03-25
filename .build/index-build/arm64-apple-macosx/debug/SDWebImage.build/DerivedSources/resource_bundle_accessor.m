#import <Foundation/Foundation.h>

NSBundle* SDWebImage_SWIFTPM_MODULE_BUNDLE() {
    NSURL *bundleURL = [[[NSBundle mainBundle] bundleURL] URLByAppendingPathComponent:@"SDWebImage_SDWebImage.bundle"];

    NSBundle *preferredBundle = [NSBundle bundleWithURL:bundleURL];
    if (preferredBundle == nil) {
      return [NSBundle bundleWithPath:@"/Users/adityachoubey/Code/digia_engage/.build/index-build/arm64-apple-macosx/debug/SDWebImage_SDWebImage.bundle"];
    }

    return preferredBundle;
}