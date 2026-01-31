import { createSlice } from '@reduxjs/toolkit';
import { postChatbot, getChatbot } from '../functions/axiosFunctions';
import { IChatMessage } from '../interfaces';

interface ChatbotState {
  loading: boolean;
  messages: IChatMessage[];
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
      // getChatbot
      .addCase(getChatbot.pending, (state) => {
        state.loading = true;
      })
      .addCase(getChatbot.fulfilled, (state, action) => {
        state.loading = false;
        state.messages = action.payload;
        state.error = '';
      })
      .addCase(getChatbot.rejected, (state, action) => {
        state.loading = false;
        state.error = (action.payload as string) || action.error.message || 'An error occurred';
      })
      // postChatbot
      .addCase(postChatbot.pending, (state, action) => {
        state.loading = true;
        // Optimistic update can be tricky with full object, we'll wait for response or just add user part first if we pass it in action.meta.arg
        // For now, let's append the user's message here if we want instant feedback, but check how we call it.
        if (action.meta.arg) {
          state.messages.push({ role: 'user', text: action.meta.arg } as IChatMessage);
        }
      })
      .addCase(postChatbot.fulfilled, (state, action) => {
        state.loading = false;
        state.messages.push(action.payload as IChatMessage); // Payload is now { role: 'model', text: ... }
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