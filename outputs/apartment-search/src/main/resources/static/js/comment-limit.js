(() => {
  document.querySelectorAll('[data-comment-limit]').forEach(input => {
    const form = input.form;
    const count = form.querySelector('#comment-count');
    const button = form.querySelector('button[type="submit"]');
    const limit = Number(input.dataset.commentLimit);
    const validate = () => {
      const length = Array.from(input.value).length;
      const over = length > limit;
      count.textContent = `${length} / ${limit}자${over ? ' · 최대 글자 수를 초과했습니다.' : ''}`;
      input.classList.toggle('comment-over-limit', over);
      count.classList.toggle('comment-over-limit', over);
      input.setAttribute('aria-invalid', String(over));
      input.setCustomValidity(over ? `댓글은 ${limit}자 이내로 입력하세요.` : '');
      button.disabled = over || Boolean(form.dataset.pending);
      return !over;
    };
    input.addEventListener('input', validate);
    form.addEventListener('submit', event => {
      if (!validate()) event.preventDefault();
    });
    window.addEventListener('pageshow', validate);
    validate();
  });
})();
