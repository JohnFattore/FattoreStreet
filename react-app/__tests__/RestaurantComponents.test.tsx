// @vitest-environment jsdom
import { screen, waitFor, within } from "@testing-library/react";
import { expect, describe, it, vi } from "vitest";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import { http } from "msw";
import RestaurantTable from "../src/components/restaurants/RestaurantTable";
import ReviewTable from "../src/components/restaurants/ReviewTable";
import ReviewForm from "../src/components/restaurants/ReviewForm";
import { IRestaurant } from "../src/interfaces";
import { renderWithProviders } from "./testutils";
import { server } from "./mocks/server";

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
  ? import.meta.env.VITE_APP_DJANGO_URL
  : `${import.meta.env.VITE_APP_DJANGO_URL}/`;
const restaurantsApiBaseUrl = djangoBaseUrl + "restaurants/api/";

const authenticatedState = {
  user: { access: "fake-token", refresh: "fake-refresh", username: "testUser" },
};

const sonic: IRestaurant = {
  yelp_id: "bBDDEgkFA1Otx9Lfe7BZUQ",
  name: "Sonic Drive-In",
  address: "2312 Dickerson Pike",
  state: "TN",
  city: "Nashville",
  latitude: 36.2081024,
  longitude: -86.7681696,
  categories: "Fast Food",
  stars: "1.5",
  review_count: 10,
  id: 104585,
};

describe("RestaurantTable", () => {
  it("renders restaurants from the API", async () => {
    renderWithProviders(<RestaurantTable setRestaurant={vi.fn()} />);

    expect(await screen.findByText("Sonic Drive-In")).toBeInTheDocument();
    expect(screen.getByText("2312 Dickerson Pike")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /create review/i }),
    ).toBeInTheDocument();
  });

  it("filters restaurants with the search bar", async () => {
    renderWithProviders(<RestaurantTable setRestaurant={vi.fn()} />);

    expect(await screen.findByText("Sonic Drive-In")).toBeInTheDocument();

    await userEvent.type(
      screen.getByPlaceholderText("Restaurant Search"),
      "zzz",
    );
    expect(await screen.findByRole("noModels")).toBeInTheDocument();
  });

  it("sorts rows when a column header is clicked", async () => {
    server.use(
      http.get(restaurantsApiBaseUrl.concat("restaurant-list-create/"), () =>
        Response.json(
          [
            { ...sonic, id: 1, name: "Burger Barn", stars: "3.0" },
            { ...sonic, id: 2, name: "Arnold's Country Kitchen", stars: "4.5" },
          ],
          { status: 200 },
        ),
      ),
    );

    renderWithProviders(<RestaurantTable setRestaurant={vi.fn()} />);

    expect(await screen.findByText("Burger Barn")).toBeInTheDocument();

    // Initial sort is ascending by the first column (name)
    let rows = screen.getAllByRole("row");
    expect(
      within(rows[1]).getByText("Arnold's Country Kitchen"),
    ).toBeInTheDocument();

    // Clicking the active column toggles to descending
    await userEvent.click(screen.getByText(/^Restaurant/));
    rows = screen.getAllByRole("row");
    expect(within(rows[1]).getByText("Burger Barn")).toBeInTheDocument();
  });

  it("calls setRestaurant when Create Review is clicked", async () => {
    const setRestaurant = vi.fn();
    renderWithProviders(<RestaurantTable setRestaurant={setRestaurant} />);

    await userEvent.click(
      await screen.findByRole("button", { name: /create review/i }),
    );
    expect(setRestaurant).toHaveBeenCalledWith(
      expect.objectContaining({ name: "Sonic Drive-In" }),
    );
  });
});

describe("ReviewTable", () => {
  it("renders nothing when not authenticated", () => {
    const { container } = renderWithProviders(<ReviewTable />);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders the user's reviews", async () => {
    renderWithProviders(<ReviewTable />, {
      preloadedState: authenticatedState,
    });

    expect(await screen.findByText("Mike's Ice Cream")).toBeInTheDocument();
    expect(screen.getByText("3 - Good")).toBeInTheDocument();
    expect(screen.getByText("ice cream")).toBeInTheDocument();
  });

  it("removes a review after deleting it", async () => {
    let deleted = false;
    server.use(
      http.get(restaurantsApiBaseUrl.concat("review-list/"), () =>
        Response.json(
          deleted
            ? []
            : [
                {
                  user: 2,
                  rating: "3.0",
                  comment: "ice cream",
                  id: 17,
                  restaurant: 104622,
                  restaurant_detail: {
                    name: "Mike's Ice Cream",
                    latitude: "36.16264920",
                    longitude: "-86.77597330",
                  },
                },
              ],
          { status: 200 },
        ),
      ),
      http.delete(
        new RegExp(
          restaurantsApiBaseUrl.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") +
            "review/\\d+/",
        ),
        () => {
          deleted = true;
          return new Response(null, { status: 204 });
        },
      ),
    );

    renderWithProviders(<ReviewTable />, {
      preloadedState: authenticatedState,
    });

    expect(await screen.findByText("Mike's Ice Cream")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /delete/i }));

    // Deleting invalidates the Reviews tag, which refetches the (now empty) list
    expect(
      await screen.findByText("testUser has no reviews"),
    ).toBeInTheDocument();
  });
});

describe("ReviewForm", () => {
  it("submits a review and shows the success message", async () => {
    renderWithProviders(
      <ReviewForm restaurant={sonic} show={true} onHide={vi.fn()} />,
      { preloadedState: authenticatedState },
    );

    await userEvent.selectOptions(screen.getByLabelText("Rating"), "5");
    await userEvent.type(screen.getByPlaceholderText("Comment"), "Great tots");
    await userEvent.click(
      screen.getByRole("button", { name: /submit review/i }),
    );

    await waitFor(() => {
      expect(
        screen.getByText("Review for Sonic Drive-In submitted"),
      ).toBeInTheDocument();
    });
  });

  it("shows the API error when the review is rejected", async () => {
    server.use(
      http.post(restaurantsApiBaseUrl.concat("review-create/"), () =>
        Response.json(
          { detail: "You have already reviewed this restaurant." },
          { status: 400 },
        ),
      ),
    );

    renderWithProviders(
      <ReviewForm restaurant={sonic} show={true} onHide={vi.fn()} />,
      { preloadedState: authenticatedState },
    );

    await userEvent.selectOptions(screen.getByLabelText("Rating"), "5");
    await userEvent.type(screen.getByPlaceholderText("Comment"), "Great tots");
    await userEvent.click(
      screen.getByRole("button", { name: /submit review/i }),
    );

    expect(
      await screen.findByText("You have already reviewed this restaurant."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/submitted/)).not.toBeInTheDocument();
  });
});
