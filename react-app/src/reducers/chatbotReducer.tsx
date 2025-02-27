import { createSlice } from '@reduxjs/toolkit';
import { postChatbot } from '../components/axiosFunctions';

interface ChatbotState {
  loading: boolean;
  messages: string[];
  error: string;
}

const initialState: ChatbotState = {
  loading: false,
  messages: [],
  error: '',
};

const chatbotSlice = createSlice({
  name: 'chatbot',
  initialState,
  reducers: {
    errorChatbot: (state, action) => {
      state.error = action.payload;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(postChatbot.pending, (state) => {
        state.loading = true;
      })
      .addCase(postChatbot.fulfilled, (state, action) => {
        state.loading = false;
        state.messages.push(action.payload);
        state.error = '';
      })
      .addCase(postChatbot.rejected, (state, action) => {
        state.loading = false;
        state.error = (action.payload as string) || action.error.message || 'An error occurred';
      })      
  },
});

export const { errorChatbot } = chatbotSlice.actions;
export default chatbotSlice.reducer;