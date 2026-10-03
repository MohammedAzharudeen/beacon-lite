package com.beacon.adapter.model;

/**
 * Image alt text, sampled from product JSON ({@code /products.json} has no alt field).
 *
 * @param sampled products read
 * @param productsWithAllAlt products whose every image has alt text
 * @param images images seen
 * @param imagesWithAlt images with alt text
 */
public record AltTextSample(
    SourceStatus status, int sampled, int productsWithAllAlt, int images, int imagesWithAlt) {

  public static AltTextSample none(SourceStatus status) {
    return new AltTextSample(status, 0, 0, 0, 0);
  }
}
