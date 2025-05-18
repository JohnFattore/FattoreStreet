import axios from "axios";
import { IAsset, IRestaurant, IReview } from "../interfaces";
import { createAsyncThunk } from "@reduxjs/toolkit";
import { RootState } from "../main";
import { fetchQuote } from "./helperFunctions";

interface ISnp500Date {
  id: number;
  date: string; // ISO date string (e.g., "2024-12-18")
  price: number; // Stored as a string
}

interface IAssetInfo {
  id: number;
  ticker: string;
  short_name: string;
  long_name: string;
  type: string;
  exchange: string;
  market: string;
}

interface IAssetResponse {
  id: number;
  ticker: string;
  shares: number;
  cost_basis: number;
  sell_price: number | null;
  buy_date: string; // ISO date string (e.g., "2024-03-01")
  sell_date: string | null; // Nullable if not sold
  user: number; // Assuming this is a user ID
  asset_info: IAssetInfo;
  snp500_buy_date: ISnp500Date;
  snp500_sell_date: ISnp500Date | null;
}

interface IPostAsset {
  ticker: string;
  shares: number;
  buyDate: string;
}

async function formatIAsset(asset: IAssetResponse): Promise<IAsset> {
  const quoteSPY = await fetchQuote("SPY");
  const quote = await fetchQuote(asset.asset_info.ticker);
  const currentPrice = quote.price * asset.shares;
  var snp500PercentChange =
    (quoteSPY.price - asset.snp500_buy_date.price) /
    asset.snp500_buy_date.price;
  var snp500PriceSell: number | null = null;
  var sellDate: string | null = null;
  var sellPrice: number | null = null;
  var percentChange = (currentPrice - asset.cost_basis) / asset.cost_basis;
  if (asset.sell_price && asset.snp500_sell_date) {
    snp500PriceSell = asset.snp500_sell_date.price;
    snp500PercentChange =
      (asset.snp500_sell_date.price - asset.snp500_buy_date.price) /
      asset.snp500_buy_date.price;
    sellDate = asset.sell_date;
    sellPrice = asset.sell_price;
    percentChange = (asset.sell_price - asset.cost_basis) / asset.cost_basis;
  }

  return {
    ticker: asset.asset_info.ticker,
    short_name: asset.asset_info.short_name,
    long_name: asset.asset_info.long_name,
    type: asset.asset_info.type,
    exchange: asset.asset_info.exchange,
    market: asset.asset_info.market,
    shares: asset.shares,
    costBasis: asset.cost_basis,
    sellPrice: sellPrice,
    buyDate: asset.buy_date,
    sellDate: sellDate,
    currentPrice: currentPrice,
    percentChange: percentChange,
    snp500PriceBuy: asset.snp500_buy_date.price,
    snp500PriceSell: snp500PriceSell,
    snp500PercentChange: snp500PercentChange,
    id: asset.id,
  };
}

function handleError(error: any, rejectWithValue: any, defaultMessage: string) {
  const errors = error.response?.data;

  if (errors?.detail) {
    return rejectWithValue(errors.detail);
  }

  if (typeof errors === "object") {
    // Return the first field-level error found
    const firstField = Object.keys(errors)[0];
    const message = Array.isArray(errors[firstField])
      ? errors[firstField][0]
      : errors[firstField];
    return rejectWithValue(message);
  }

  return rejectWithValue(defaultMessage);
}

export const login = createAsyncThunk(
  "users/login",
  async (
    { username, password }: { username: string; password: string },
    { rejectWithValue }
  ) => {
    try {
      const response = await axios.post(
        import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("token/"),
        {
          username: username,
          password: password,
        }
      );

      const { access, refresh } = response.data;

      return { username, access, refresh };
    } catch (error: any) {
      return rejectWithValue(error.response.data.detail || "Login failed");
    }
  }
);

export const refreshLogin = createAsyncThunk(
  "users/refreshLogin",
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const refresh = state.user.refresh;
      const response = await axios.post(
        import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("token/refresh/"),
        {
          refresh: refresh,
        }
      );

      const { access } = response.data;

      return { access };
    } catch (error: any) {
      return rejectWithValue(
        error.response.data.detail || "Refresh Login failed"
      );
    }
  }
);

export const postUser = createAsyncThunk(
  "users/postUser",
  async (
    {
      username,
      password,
      email,
    }: { username: string; password: string; email: string },
    { rejectWithValue }
  ) => {
    try {
      const response = await axios.post(
        import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("users/"),
        {
          username: username,
          password: password,
          email: email,
        }
      );
      return response.data;
    } catch (error: any) {
      return rejectWithValue(
        error.response.data.username ||
          error.response.data.detail ||
          "Registering user failed"
      );
    }
  }
);

export const getAssets = createAsyncThunk(
  "assets/getAssets",
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;

      if (!access) {
        return rejectWithValue("Unauthorized: No access token found");
      }

      const response = await axios.get(
        import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("assets/"),
        {
          headers: {
            Authorization: " Bearer ".concat(access),
          },
        }
      );

      // ensure formatIAsset calls are made sequenially, not in parallel
      // this ensures the SPY quote call at the beginning isnt called for each asset
      const assets: IAsset[] = [];
      for (const asset of response.data) {
        const formatted = await formatIAsset(asset);
        assets.push(formatted);
      }

      return assets;
    } catch (error: any) {
      return rejectWithValue(
        error.response.data.detail || "Getting Assets failed"
      );
    }
  }
);
export const postAsset = createAsyncThunk(
  "assets/postAsset",
  async (asset: IPostAsset, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      const response = await axios.post(
        import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("assets/"),
        {
          ticker: asset.ticker,
          shares: asset.shares,
          buy_date: asset.buyDate,
        },
        {
          headers: {
            Authorization: " Bearer ".concat(access),
          },
        }
      );
      return formatIAsset(response.data);
    } catch (error: any) {
      return handleError(error, rejectWithValue, "Error adding asset");
    }
  }
);

export const deleteAsset = createAsyncThunk(
  "assets/deleteAsset",
  async (id: number, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      await axios.delete(
        import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("asset/", id, "/"),
        {
          headers: {
            Authorization: " Bearer ".concat(access),
          },
        }
      );
      return { id: id };
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.detail || "Deleting Asset failed"
      );
    }
  }
);

export const sellAsset = createAsyncThunk(
  "assets/sellAsset",
  async (
    { id, sellDate }: { id: number; sellDate: string },
    { getState, rejectWithValue }
  ) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      const response = await axios.patch(
        import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat(
          "update-sell-date/",
          id,
          "/"
        ),
        { sell_date: sellDate },
        {
          headers: {
            Authorization: " Bearer ".concat(access),
          },
        }
      );

      return formatIAsset(response.data);
    } catch (error: any) {
      return handleError(error, rejectWithValue, "Error selling asset");
    }
  }
);

export const getSnP500Price = createAsyncThunk(
  "SnP500Prices/getSnP500",
  async (snp500Date: string, { rejectWithValue }) => {
    try {
      const response = await axios.get(
        import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("snp500-price/"),
        {
          params: {
            date: snp500Date,
          },
        }
      );
      const SnP500Data = response.data;
      const quote = await fetchQuote("SPY");
      return {
        date: SnP500Data.date,
        costBasis: SnP500Data.price,
        currentPrice: quote.price,
        percentChange: (quote.price - SnP500Data.price) / SnP500Data.price,
        id: SnP500Data.id,
      };
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.detail || "Adding S&P 500 date failed"
      );
    }
  }
);

// currently unused
export const getAsset = async (id: number) => {
  const response = await axios.get(
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("asset/", id, "/"),
    {
      headers: {
        Authorization: " Bearer ".concat(
          sessionStorage.getItem("token") as string
        ),
      },
    }
  );
  return response;
};

export const getQuote = async (ticker: string) => {
  const response = await axios.get(
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("quote/"),
    {
      params: {
        symbol: ticker,
      },
    }
  );
  return response;
};

export const getFinancials = async (ticker: string) => {
  const response = await axios.get(
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("financials/"),
    {
      params: {
        symbol: ticker,
      },
    }
  );
  return response;
};

// only works for stocks
export const getCompanyProfile2 = async (ticker: string) => {
  const response = await axios.get(
    import.meta.env.VITE_APP_FINNHUB_URL.concat("stock/profile2/"),
    {
      params: {
        symbol: ticker,
        token: import.meta.env.VITE_APP_FINNHUB_KEY,
      },
    }
  );
  return response;
};

/********************************* Indexes *************************************/
export const getIndexMembers = async () => {
  const response = await axios.get(
    import.meta.env.VITE_APP_DJANGO_INDEXES_URL.concat("index_members/")
  );
  return response;
};

export const patchIndexMembers = async (notes: string, id: number) => {
  const response = await axios.patch(
    import.meta.env.VITE_APP_DJANGO_INDEXES_URL.concat(
      "index_members_update/",
      id,
      "/"
    ),
    {
      notes: notes,
      // 1 is a placeholder, this is actually set on the back end using the User object returned by the request
      user: 1,
    },
    {
      headers: {
        Authorization: " Bearer ".concat(
          sessionStorage.getItem("token") as string
        ),
      },
    }
  );
  return response;
};

/********************************* Restaurants *************************************/
export const getRestaurants = createAsyncThunk<IRestaurant[]>(
  "restaurants/getRestaurants",
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const location = state.location;
      const response = await axios.get(
        import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat(
          "restaurant-list-create/"
        ),
        {
          params: {
            state: location.state,
            city: location.city,
          },
        }
      );
      const transformedData: IRestaurant[] = await Promise.all(
        response.data.map(async (restaurant: any) => {
          return {
            yelp_id: restaurant.yelp_id,
            name: restaurant.name,
            address: restaurant.address,
            state: restaurant.state,
            city: restaurant.city,
            latitude: restaurant.latitude,
            longitude: restaurant.longitude,
            categories: restaurant.categories,
            stars: restaurant.stars,
            review_count: restaurant.review_count,
            id: restaurant.id,
          };
        })
      );
      return transformedData;
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.detail || "Getting Restaurants failed"
      );
    }
  }
);

export const getRestaurantRecommendations = createAsyncThunk<IRestaurant[]>(
  "restaurants/getRestaurantRecommendations",
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      const response = await axios.get(
        import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat(
          "restaurant-recommend/"
        ),
        {
          headers: {
            Authorization: " Bearer ".concat(access),
          },
        }
      );
      const transformedData: IRestaurant[] = await Promise.all(
        response.data.map(async (restaurant: any) => {
          return {
            yelp_id: restaurant.yelp_id,
            name: restaurant.name,
            address: restaurant.address,
            state: restaurant.state,
            city: restaurant.city,
            latitude: restaurant.latitude,
            longitude: restaurant.longitude,
            categories: restaurant.categories,
            stars: restaurant.stars,
            review_count: restaurant.review_count,
            id: restaurant.id,
          };
        })
      );
      return transformedData;
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.detail || "Getting Restaurants failed"
      );
    }
  }
);

export const getReviews = createAsyncThunk<IReview[]>(
  "reviews/getReviews",
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      const response = await axios.get(
        import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat("review-list/"),
        {
          headers: {
            Authorization: " Bearer ".concat(access),
          },
        }
      );
      const transformedData: IReview[] = await Promise.all(
        response.data.map(async (review: any) => {
          return {
            restaurant: review.restaurant,
            name: review.restaurant_detail.name,
            user: review.user,
            rating: Number(review.rating),
            comment: review.comment,
            latitude: review.restaurant_detail.latitude,
            longitude: review.restaurant_detail.longitude,
            id: review.id,
          };
        })
      );
      return transformedData;
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.detail || "Getting Review failed"
      );
    }
  }
);

export const postReview = createAsyncThunk(
  "reviews/postReview",
  async (review: IReview, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      const response = await axios.post(
        import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat(
          "review-create/"
        ),
        {
          restaurant: review.restaurant,
          user: 1,
          rating: Number(review.rating),
          comment: review.comment,
        },
        {
          headers: {
            Authorization: " Bearer ".concat(access),
          },
        }
      );
      return response.data;
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.detail ||
          error.response?.data?.non_field_errors ||
          "Adding Review failed"
      );
    }
  }
);

export const deleteReview = createAsyncThunk(
  "reviews/deleteReview",
  async (id: number, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      await axios.delete(
        import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat(
          "review/",
          id,
          "/"
        ),
        {
          headers: {
            Authorization: " Bearer ".concat(access),
          },
        }
      );
      return { id: id };
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.detail || "Deleting Review failed"
      );
    }
  }
);

export const patchReview = createAsyncThunk(
  "reviews/patchReview",
  async (review: IReview, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      const response = await axios.patch(
        import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat(
          "review-update/",
          review.id,
          "/"
        ),
        {
          rating: review.rating,
        },
        {
          headers: {
            Authorization: " Bearer ".concat(access),
          },
        }
      );
      return response.data;
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.detail || "Updating Review failed"
      );
    }
  }
);

// this doesnt quite work, state
/*
export const getReview = async (id: number) => {
  const access = store.getState().user.access
  const response = await axios.get(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("review/", id, "/"), {
    headers: {
      'Authorization': ' Bearer '.concat(access)
    },
  });
  return response
}
  */

export const postChatbot = createAsyncThunk(
  "chatbot/postChatbot",
  async (message: string, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      const response = await axios.post(
        import.meta.env.VITE_APP_DJANGO_CHATBOT_URL.concat("chatbot/"),
        {
          message: message,
        },
        {
          headers: {
            Authorization: " Bearer ".concat(access),
          },
        }
      );
      return response.data["message"];
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.detail ||
          error.response?.data?.non_field_errors ||
          "Posting chatbot failed"
      );
    }
  }
);
