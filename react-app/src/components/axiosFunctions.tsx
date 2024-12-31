import axios from 'axios';
import { IAsset, IRestaurant } from '../interfaces';
import { createAsyncThunk } from '@reduxjs/toolkit';
import { RootState } from '../main';

export const login = createAsyncThunk('users/login',
  async ({ username, password }: { username: string; password: string }, { rejectWithValue }) => {
    try {
      const response = await axios.post(import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("token/"), {
        username: username,
        password: password
      });

    const { access, refresh } = response.data;

    return {username, access, refresh}
    }
    catch (error: any) {
      if (error.response && error.response.data) {
        // Reject with the custom server error message
        return rejectWithValue(error.response.data.detail || 'Login failed');
      }
      else {
        return rejectWithValue('Login failed');
      }
    }
  }
)

export const refreshLogin = createAsyncThunk('users/refreshLogin',
  async (_, {getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const refresh = state.user.refresh;
      const response = await axios.post(import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("token/refresh/"), {
        refresh: refresh,
      });

    const { access } = response.data;

    return {access, refresh}
    }
    catch (error: any) {
      if (error.response && error.response.data) {
        // Reject with the custom server error message
        return rejectWithValue(error.response.data.detail || 'Refresh Login failed');
      }
      else {
        return rejectWithValue('Refresh Login failed');
      }
    }
  }
)

// register would conflict with the useForm hook
export const postUser = async (username: string, password: string, email: string) => {
  const response = await axios.post(import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("users/"), {
    username: username,
    password: password,
    email: email,
  })
  return response
}

export const getAssets = createAsyncThunk<IAsset[]>('assets/getAssets',
  async (_, {getState}) => {
    const state = getState() as RootState;
    const access = state.user.access;
    const response = await axios.get(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("assets/"), {
      headers: {
        'Authorization': ' Bearer '.concat(access)
      },
    });
    const transformedData: IAsset[] = response.data.map((asset: any) => ({
      ticker: asset.ticker,
      shares: asset.shares,
      costbasis: asset.costbasis,
      buy: asset.buy,
      SnP500Price: asset.SnP500Price.price,
      id: asset.id
    }));
    return transformedData
  }
)
 
// still want to handle errors in these axios functions... well isnt it already handled
export const postAsset = createAsyncThunk('assets/postAsset',
  async (asset: IAsset) => {
    const response = await axios.post(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("assets/"), {
      ticker: asset.ticker,
      shares: asset.shares,
      costbasis: asset.costbasis,
      buy: asset.buy,
      // 1 is a placeholder, value set in backend
      SnP500Price: 1,
      // 1 is a placeholder, this is actually set on the back end using the User object returned by the request
      user: 1
    }, {
      headers: {
        'Authorization': ' Bearer '.concat(sessionStorage.getItem('token') as string)
      }
    });
    return response.data
  }
)

export const deleteAsset = createAsyncThunk('assets/deleteAsset',
  async (id: number) => {
    await axios.delete(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("asset/", id, "/"), {
      headers: {
        'Authorization': ' Bearer '.concat(sessionStorage.getItem('token') as string)
      },
    });
    return { id: id };
  }
)

export const getAsset = async (id: number) => {
  const response = await axios.get(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("asset/", id, "/"), {
    headers: {
      'Authorization': ' Bearer '.concat(sessionStorage.getItem('token') as string)
    },
  });
  return response
}

export const getQuote = async (ticker: string) => {
  const response = await axios.get(import.meta.env.VITE_APP_FINNHUB_URL.concat("quote/"), {
    params: {
      symbol: ticker,
      token: import.meta.env.VITE_APP_FINNHUB_KEY
    }
  });
  return response
}

// only works for stocks
export const getCompanyProfile2 = async (ticker: string) => {
  const response = await axios.get(import.meta.env.VITE_APP_FINNHUB_URL.concat("stock/profile2/"), {
    params: {
      symbol: ticker,
      token: import.meta.env.VITE_APP_FINNHUB_KEY
    }
  });
  return response
}

/********************************* Indexes *************************************/
export const getIndexMembers = async () => {
  const response = await axios.get(import.meta.env.VITE_APP_DJANGO_INDEXES_URL.concat("index_members/"));
  return response
}

export const patchIndexMembers = async (notes: string, id: number) => {
  const response = await axios.patch(import.meta.env.VITE_APP_DJANGO_INDEXES_URL.concat("index_members_update/", id, "/"), {
    notes: notes,
    // 1 is a placeholder, this is actually set on the back end using the User object returned by the request
    user: 1
  }, {
    headers: {
      'Authorization': ' Bearer '.concat(sessionStorage.getItem('token') as string),
    }
  });
  return response
}

/********************************* Restaurants *************************************/
// handle errors here, seems like a good idea, particurly when i can use redux to setMessage
export const getRestaurants = createAsyncThunk<IRestaurant[]>('restaurants/getRestaurants',
  async () => {
    const response = await axios.get(import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat("restaurant-list-create/"));
    return response.data
  }
)

export const getReviews = async () => {
  const response = await axios.get(import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat("review-list/"));
  return response
}

export const postReview = async (restaurant: number, rating: number, comment: string) => {
  const response = await axios.post(import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat("review-create/"), {
    restaurant: restaurant,
    user: 1,
    rating: rating,
    comment: comment,
  }, {
    headers: {
      'Authorization': ' Bearer '.concat(sessionStorage.getItem('token') as string),
    }
  });
  return response
}