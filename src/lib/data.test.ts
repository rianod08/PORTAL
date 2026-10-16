import { beforeEach, describe, expect, it } from "vitest";
import {
  clearAnalytics,
  createBusiness,
  generateUniqueSlug,
  getAnalyticsSummary,
  getBookingLeads,
  recordEvent,
  resetDemoData,
  submitBookingLead,
} from "./data";

describe("Portal local data services", () => {
  beforeEach(() => {
    resetDemoData();
  });

  it("generates unique slugs", () => {
    expect(generateUniqueSlug("Bloom Cafe")).toBe("bloom-cafe-2");
    createBusiness({
      name: "Bloom Cafe 2",
      type: "Restaurant / Cafe",
      description: "Second venue",
      address: "1 Test Street",
      phone: "123",
      email: "test@example.com",
    });
    expect(generateUniqueSlug("Bloom Cafe 2")).toBe("bloom-cafe-2-2");
  });

  it("aggregates analytics events from recorded actions", () => {
    clearAnalytics("biz-bloom");
    recordEvent({ businessId: "biz-bloom", eventType: "page_view", source: "qr" });
    recordEvent({ businessId: "biz-bloom", eventType: "page_view", source: "direct" });
    recordEvent({ businessId: "biz-bloom", eventType: "booking_submit", targetLabel: "Book a Table" });
    recordEvent({ businessId: "biz-bloom", eventType: "hotspot_click", targetLabel: "Reserve this table" });

    const summary = getAnalyticsSummary("biz-bloom");
    expect(summary.totalVisits).toBe(2);
    expect(summary.bookingSubmissions).toBe(1);
    expect(summary.hotspotClicks).toBe(1);
    expect(summary.conversionRate).toBe(50);
  });

  it("creates booking leads", () => {
    const lead = submitBookingLead({
      businessId: "biz-bloom",
      source: "shopfront",
      customerName: "Jordan Lee",
      customerEmail: "jordan@example.com",
      partySize: 3,
    });

    const leads = getBookingLeads("biz-bloom");
    expect(lead.status).toBe("new");
    expect(leads.some((item) => item.id === lead.id)).toBe(true);
  });
});
