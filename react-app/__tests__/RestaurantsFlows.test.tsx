// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import { expect, describe, it, vi } from "vitest";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import { http } from "msw";
import ReviewMap from "../src/components/restaurants/ReviewMap";
import RestaurantRecommend from "../src/components/restaurants/RestaurantRecommend";
import LocationForm from "../src/components/restaurants/LocationForm";
import Restaurants from "../src/pages/Restaurants";
import { renderWithProviders } from "./testutils";
import { server } from "./mocks/server";

const restaurantsApiBaseUrl =
  ((import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
    ? import.meta.env.VITE_APP_DJANGO_URL
    : `${import.meta.env.VITE_APP_DJANGO_URL}/`) + "restaurants/api/";

const authenticatedState = {
  user: { access: "fake-token", refresh: "fake-refresh", username: "testUser" },
};

const { map, tileLayer, marker, bindPopup } = vi.hoisted(() => {
  const bindPopup = vi.fn().mockReturnValue({ openPopup: vi.fn() });
  const marker = vi.fn().mockReturnValue({
    addTo: vi.fn().mockReturnValue({ bindPopup }),
  });
  const mapInstance: Record<string, unknown> = { remove: vi.fn() };
  mapInstance.setView = vi.fn().mockReturnValue(mapInstance);
  const map = vi.fn().mockReturnValue(mapInstance);
  const tileLayer = vi.fn().mockReturnValue({ addTo: vi.fn() });
  return { map, tileLayer, marker, bindPopup };
});

vi.mock("leaflet", () => {
  const L = {
    map,
    tileLayer,
    marker,
    AwesomeMarkers: { icon: vi.fn().mockReturnValue({}) },
  };
  return { default: L, ...L };
});
vi.mock("leaflet.awesome-markers", () => ({}));
vi.mock("leaflet/dist/leaflet.css", () => ({}));
vi.mock("leaflet.awesome-markers/dist/leaflet.awesome-markers.css", () => ({}));

describe("ReviewMap", () => {
  it("initializes the map and adds a marker per review", async () => {
    renderWithProviders(<ReviewMap />, { preloadedState: authenticatedState });

    expect(map).toHaveBeenCalledWith("map");
    // Default review-list handler returns Mike's Ice Cream with rating 3
    await waitFor(() => {
      expect(bindPopup).toHaveBeenCalledWith("Mike's Ice Cream: 3");
    });
  });

  it("adds no markers when logged out", () => {
    renderWithProviders(<ReviewMap />);
    expect(map).toHaveBeenCalledWith("map");
    expect(bindPopup).not.toHaveBeenCalled();
  });
});

describe("RestaurantRecommend (dormant feature)", () => {
  it("shows the fetch button until clicked, then renders recommendations", async () => {
    renderWithProviders(<RestaurantRecommend setRestaurant={vi.fn()} />, {
      preloadedState: authenticatedState,
    });

    await userEvent.click(
      screen.getByRole("button", { name: "Click for Recommendations" }),
    );

    expect(
      await screen.findByText("Recommended Restaurants"),
    ).toBeInTheDocument();
    expect(await screen.findByText("Prince's Hot Chicken")).toBeInTheDocument();
  });

  it("shows the API error when the fetch fails", async () => {
    server.use(
      http.get(restaurantsApiBaseUrl.concat("restaurant-recommend/"), () =>
        Response.json({ detail: "model not loaded" }, { status: 500 }),
      ),
    );

    renderWithProviders(<RestaurantRecommend setRestaurant={vi.fn()} />, {
      preloadedState: authenticatedState,
    });

    await userEvent.click(
      screen.getByRole("button", { name: "Click for Recommendations" }),
    );
    expect(await screen.findByText("model not loaded")).toBeInTheDocument();
  });
});

describe("LocationForm", () => {
  it("updates the location slice on submit", async () => {
    const { store } = renderWithProviders(<LocationForm />);

    await userEvent.selectOptions(screen.getByRole("combobox"), "CA");
    await userEvent.type(screen.getByPlaceholderText("City"), "San Diego");
    await userEvent.click(
      screen.getByRole("button", { name: "Update Location" }),
    );

    await waitFor(() => {
      expect(store.getState().location).toMatchObject({
        state: "CA",
        city: "San Diego",
      });
    });
  });

  it("requires a city", async () => {
    const { store } = renderWithProviders(<LocationForm />);

    await userEvent.selectOptions(screen.getByRole("combobox"), "CA");
    await userEvent.click(
      screen.getByRole("button", { name: "Update Location" }),
    );

    expect(
      await screen.findByText(/City field is required/),
    ).toBeInTheDocument();
    expect(store.getState().location.city).toBe("Nashville");
  });
});

describe("Restaurants page", () => {
  it("gates logged-out users but still shows the public restaurant table", async () => {
    renderWithProviders(<Restaurants />);

    expect(
      screen.getByText("Nashville Restaurant Explorer"),
    ).toBeInTheDocument();
    expect(await screen.findByText("Sonic Drive-In")).toBeInTheDocument();
  });

  it("toggles the review map for logged-in users", async () => {
    renderWithProviders(<Restaurants />, {
      preloadedState: authenticatedState,
    });

    expect(
      await screen.findByText("Nashville Restaurants"),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Show Map" }));
    expect(map).toHaveBeenCalledWith("map");
    expect(
      screen.getByRole("button", { name: "Hide Map" }),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Hide Map" }));
    expect(
      screen.getByRole("button", { name: "Show Map" }),
    ).toBeInTheDocument();
  });
});
