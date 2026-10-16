import { describe, expect, it } from "vitest";
import {
  emptyVenue,
  validateVenue,
  validVenueSlug,
  venueButtonHref,
  type VenueView,
} from "./venue-contract";
describe("connected storefront contract", () => {
  const doc = () => emptyVenue("Test cafe");
  it("accepts venue details but rejects malformed hours, buttons and theme overrides", () => {
    expect(validateVenue(doc())).toEqual([]);
    expect(validateVenue({ ...doc(), openingHours: null })).not.toEqual([]);
    expect(
      validateVenue({
        ...doc(),
        openingHours: doc().openingHours.map((h) => ({ ...h, open: "25:99" })),
      }),
    ).not.toEqual([]);
    expect(
      validateVenue({
        ...doc(),
        buttons: [doc().buttons[0], doc().buttons[0]],
      }),
    ).not.toEqual([]);
    expect(
      validateVenue({
        ...doc(),
        themeOverrides: { backgroundStyle: "url(evil)" },
      }),
    ).not.toEqual([]);
  });
  it("rejects executable links, arbitrary image locations and reserved tour slugs", () => {
    expect(
      validateVenue({ ...doc(), bookingUrl: "javascript:alert(1)" }),
    ).not.toEqual([]);
    expect(
      validateVenue({ ...doc(), logoAssetPath: "../private.jpg" }),
    ).not.toEqual([]);
    expect(validVenueSlug("tour-12345678")).toBe(false);
    expect(validVenueSlug("my-cafe")).toBe(true);
  });
  it("only exposes available actions and routes tours through the storefront", () => {
    const view: VenueView = {
      id: "venue",
      slug: "my-cafe",
      document: doc(),
      revision: 1,
      tourSlug: null,
    };
    expect(venueButtonHref(view.document.buttons[0], view)).toBeNull();
    expect(
      venueButtonHref(view.document.buttons[0], {
        ...view,
        tourSlug: "tour-abc",
      }),
    ).toBe("/v/my-cafe/tour");
    expect(venueButtonHref(view.document.buttons[1], view)).toBeNull();
    expect(
      venueButtonHref(view.document.buttons[1], {
        ...view,
        document: { ...view.document, bookingUrl: "https://booking.example/" },
      }),
    ).toBe("https://booking.example/");
    expect(
      venueButtonHref(
        { ...view.document.buttons[1], url: "javascript:alert(1)" },
        view,
      ),
    ).toBeNull();
  });
});
