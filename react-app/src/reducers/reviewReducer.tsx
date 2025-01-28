import { createSlice } from '@reduxjs/toolkit';
import { IReview } from '../interfaces';
import { deleteReview, getReviews, patchReview, postReview } from '../components/axiosFunctions';

function sortReviews(reviews: IReview[], sortColumn: keyof IReview, sortDirection: 'asc' | 'desc'): IReview[] {
  return [...reviews].sort((a, b) => {
    const aValue = a[sortColumn];
    const bValue = b[sortColumn];

    if (aValue == null || bValue == null) return 0;

    if (aValue < bValue) return sortDirection === 'asc' ? -1 : 1;
    if (aValue > bValue) return sortDirection === 'asc' ? 1 : -1;
    return 0;
  });
}

interface ReviewState {
  loading: boolean;
  reviews: IReview[];
  error: string;
  sort: {
    sortColumn: keyof IReview | null;
    sortDirection: 'asc' | 'desc'
  }
}

const initialState: ReviewState = {
  loading: false,
  reviews: [],
  error: '',
  sort: {
    sortColumn: null,
    sortDirection: 'asc'
  }
};

const reviewSlice = createSlice({
  name: 'review',
  initialState,
  reducers: {
    setReviewSort: (state, action) => {
      const { sortColumn, sortDirection } = action.payload;
      state.sort = { sortColumn, sortDirection };
      if (sortColumn) {
        state.reviews = sortReviews(state.reviews, sortColumn, sortDirection);
      }
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(getReviews.pending, (state) => {
        state.loading = true;  // Set loading to true when the async call is pending
      })
      .addCase(getReviews.fulfilled, (state, action) => {
        state.loading = false;  // Set loading to false when the async call is fulfilled
        // this has to be more specific, 
        state.reviews = action.payload;  // Set the fetched data
      })
      .addCase(getReviews.rejected, (state, action) => {
        state.loading = false;  // Set loading to false if the call failed
        state.error = action.error.message || 'An error occurred';;  // Set the error message
      })
      .addCase(postReview.pending, (state) => {
        state.loading = true;
      })
      .addCase(postReview.fulfilled, (state, action) => {
        state.loading = false;
        let review = action.payload
        state.reviews.push({
            restaurant: review.restaurant,
            name: review.restaurant_detail.name,
            user: 1,
            rating: review.rating,
            comment: review.rating,
            id: review.id
        })
        state.error = '';
      })
      .addCase(postReview.rejected, (state, action) => {
        state.loading = false;
        state.error = (action.payload as string) || action.error.message || 'An error occurred';
      })
      .addCase(deleteReview.pending, (state) => {
        state.loading = true; 
      })
      .addCase(deleteReview.fulfilled, (state, action) => {
        state.loading = false;
        state.reviews = state.reviews.filter(review => review.id !== action.payload.id); 
        state.error = '';
      })
      .addCase(deleteReview.rejected, (state, action) => {
        state.loading = false;
        state.error = (action.payload as string) || action.error.message || 'An error occurred';
      })      
      .addCase(patchReview.pending, (state) => {
        state.loading = true; 
      })
      .addCase(patchReview.fulfilled, (state, action) => {
        state.loading = false;
        state.reviews = state.reviews.map(review =>
          review.id === action.payload.id ? action.payload : review
        );
        state.error = '';
      })
      .addCase(patchReview.rejected, (state, action) => {
        state.loading = false;
        state.error = (action.payload as string) || action.error.message || 'An error occurred';
      });        
  },
});

export const { setReviewSort } = reviewSlice.actions;
export default reviewSlice.reducer;