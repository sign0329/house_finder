(() => {
  document.querySelectorAll('[data-feedback-form]').forEach(form => {
    const field = name => form.elements.namedItem(name);
    const status = form.querySelector('.form-status');
    const validate = () => {
      const min = field('minPrice'), max = field('maxPrice');
      if (max) max.setCustomValidity(min.value && max.value && Number(min.value) > Number(max.value)
        ? '최대 가격은 최소 가격 이상으로 입력해주세요.' : '');
      const from = field('from'), to = field('to');
      if (to) to.setCustomValidity(from.value && to.value && from.value > to.value
        ? '종료일은 시작일 이후로 선택해주세요.' : '');
      const password = field('password'), confirm = field('confirmPassword');
      if (confirm) {
        confirm.setCustomValidity(confirm.value && password.value !== confirm.value ? '비밀번호가 일치하지 않습니다.' : '');
        password.setCustomValidity(new TextEncoder().encode(password.value).length > 72 ? '비밀번호는 72바이트 이내로 입력해주세요.' : '');
      }
    };
    form.addEventListener('input', validate);
    form.addEventListener('change', validate);
    form.addEventListener('submit', event => {
      validate();
      if (!form.reportValidity()) { event.preventDefault(); return; }
      if (form.dataset.pending) { event.preventDefault(); return; }
      form.dataset.pending = 'true';
      form.setAttribute('aria-busy', 'true');
      status.textContent = form.method === 'get' ? '거래를 검색하고 있습니다…' : '요청을 처리하고 있습니다…';
      form.querySelectorAll('button[type="submit"],button:not([type])').forEach(button => button.disabled = true);
    });
    window.addEventListener('pageshow', () => {
      delete form.dataset.pending;
      form.removeAttribute('aria-busy');
      status.textContent = '';
      form.querySelectorAll('button[type="submit"],button:not([type])').forEach(button => button.disabled = false);
    });
  });
})();
