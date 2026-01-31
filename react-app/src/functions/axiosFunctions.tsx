import axios from "axios";
import { IRestaurant, IReview } from "../interfaces";
import { createAsyncThunk } from "@reduxjs/toolkit";
import { RootState } from "../main";

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

// ... (previous code)

export const getChatbot = createAsyncThunk(
  "chatbot/getChatbot",
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      const response = await axios.get(
        import.meta.env.VITE_APP_DJANGO_CHATBOT_URL.concat("chatbot/"),
        {
          headers: {
            Authorization: " Bearer ".concat(access),
          },
        }
      );
      // Transform response to IChatMessage[]
      const history: any[] = [];
      response.data.forEach((interaction: any) => {
        history.push({ role: 'user', text: interaction.input_text, timestamp: interaction.timestamp });
        history.push({ role: 'model', text: interaction.output_text, timestamp: interaction.timestamp });
      });
      return history;
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.detail || "Getting chatbot history failed"
      );
    }
  }
);

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
      return { role: 'model', text: response.data["message"] };
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.detail ||
        error.response?.data?.non_field_errors ||
        "Posting chatbot failed"
      );
    }
  }
);

