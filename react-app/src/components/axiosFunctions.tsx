import axios from 'axios';
import { IAsset, IRestaurant } from '../interfaces';
import { createAsyncThunk } from '@reduxjs/toolkit';
import { RootState } from '../main';
import { fetchQuote } from './helperFunctions';

export const login = createAsyncThunk('users/login',
  async ({ username, password }: { username: string; password: string }, { rejectWithValue }) => {
    try {
      const response = await axios.post(import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("token/"), {
        username: username,
        password: password
      });

      const { access, refresh } = response.data;

      return { username, access, refresh }
    }
    catch (error: any) {
      return rejectWithValue(error.response.data.detail || 'Login failed');
    }
  }
)

export const refreshLogin = createAsyncThunk('users/refreshLogin',
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const refresh = state.user.refresh;
      const response = await axios.post(import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("token/refresh/"), {
        refresh: refresh,
      });

      const { access } = response.data;

      return { access }
    }
    catch (error: any) {
      return rejectWithValue(error.response.data.detail || 'Refresh Login failed');
    }
  }
)

export const postUser = createAsyncThunk('users/postUser',
  async ({ username, password, email }: { username: string, password: string, email: string }, { rejectWithValue }) => {
    try {
      const response = await axios.post(import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("users/"), {
        username: username,
        password: password,
        email: email,
      })
      return response.data
    }
    catch (error: any) {
      return rejectWithValue(error.response.data.username || error.response.data.detail || 'Registering user failed');
    }
  }
)

export const getAssets = createAsyncThunk('assets/getAssets',
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      const response = await axios.get(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("assets/"), {
        headers: {
          'Authorization': ' Bearer '.concat(access)
        },
      });
      const transformedData: IAsset[] = await Promise.all(response.data.map(async (asset: any) => {
        const quote = await fetchQuote(asset.ticker)
        const quoteSPY = await fetchQuote("SPY")
        const totalCostBasis = asset.shares * asset.costbasis
        const currentPrice = quote.price * asset.shares
        return {
          ticker: asset.ticker,
          shares: asset.shares,
          costBasis: asset.costbasis,
          buyDate: asset.buyDate,
          totalCostBasis: totalCostBasis,
          currentPrice: currentPrice,
          percentChange: (currentPrice - totalCostBasis) / totalCostBasis,
          SnP500Price: asset.SnP500Price.price,
          SnP500PercentChange: (quoteSPY.price - asset.SnP500Price.price) / asset.SnP500Price.price,
          id: asset.id
        }
      })
      );
      return transformedData
    }
    catch (error: any) {
      return rejectWithValue(error.response.data.detail || 'Getting Assets failed');
    }
  }
)

export const postAsset = createAsyncThunk('assets/postAsset',
  async (asset: IAsset, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      const response = await axios.post(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("assets/"), {
        ticker: asset.ticker,
        shares: asset.shares,
        buyDate: asset.buyDate,
        costbasis: 1,
        user: 1
      }, {
        headers: {
          'Authorization': ' Bearer '.concat(access)
        }
      });
      const assetData = response.data
      const quote = await fetchQuote(assetData.ticker)
      const quoteSPY = await fetchQuote("SPY")
      const totalCostBasis = assetData.shares * assetData.costbasis
      return {
        ticker: assetData.ticker,
        shares: assetData.shares,
        costBasis: assetData.costbasis,
        buyDate: assetData.buyDate,
        totalCostBasis: totalCostBasis,
        currentPrice: quote.price,
        percentChange: (quote.price - totalCostBasis) / totalCostBasis,
        SnP500Price: assetData.SnP500Price.price,
        SnP500PercentChange: (quoteSPY.price - assetData.SnP500Price.price) / assetData.SnP500Price.price,
        id: assetData.id
      }
    }
    catch (error: any) {
      return rejectWithValue(error.response?.data?.detail || 'Adding Asset failed');
    }
  }
)

export const deleteAsset = createAsyncThunk('assets/deleteAsset',
  async (id: number, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const access = state.user.access;
      await axios.delete(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("asset/", id, "/"), {
        headers: {
          'Authorization': ' Bearer '.concat(access)
        },
      });
      return { id: id };
    }
    catch (error: any) {
      return rejectWithValue(error.response?.data?.detail || 'Deleting Asset failed');
    }
  }
)

export const getSnP500Price = createAsyncThunk('SnP500Prices/getSnP500',
  async (snp500Date: string, { rejectWithValue }) => {
    try {
      const response = await axios.get(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("snp500-price/"), {
        params: {
          date: snp500Date
        }
      });
      const SnP500Data = response.data
      const quote = await fetchQuote("SPY")
      return {
        date: SnP500Data.date,
        costBasis: SnP500Data.price,
        currentPrice: quote.price,
        percentChange: (quote.price - SnP500Data.price) / SnP500Data.price,
        id: SnP500Data.id
      }
    }
    catch (error: any) {
      return rejectWithValue(error.response?.data?.detail || 'Adding S&P 500 date failed');
    }
  }
)


// currently unused
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
    const response = await axios.get(import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat("restaurant-list-create/"), {
      params: {
        state: "TN",
        city: "Nashville"
      }
    });
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